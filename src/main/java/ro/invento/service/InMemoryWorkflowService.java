package ro.invento.service;

import ro.invento.domain.*;
import ro.invento.factory.DeliveryFactory;
import ro.invento.factory.SaleFactory;
import java.time.Clock;
import java.util.*;

public final class InMemoryWorkflowService implements WorkflowService {
    private record StockKey(UUID storeId, UUID productId) { }

    private final Map<UUID, Store> stores = new LinkedHashMap<>();
    private final Map<UUID, Product> products = new LinkedHashMap<>();
    private final Map<StockKey, StoreStock> stocks = new LinkedHashMap<>();
    private final Map<UUID, Delivery> deliveries = new LinkedHashMap<>();
    private final Map<UUID, Sale> sales = new LinkedHashMap<>();
    private final ValidationService validation;
    private final ComputationService computation;
    private final DeliveryFactory deliveryFactory;
    private final SaleFactory saleFactory;
    private final AuditService audit;
    private final Clock clock;

    public InMemoryWorkflowService(ValidationService validation, ComputationService computation,
            DeliveryFactory deliveryFactory, SaleFactory saleFactory, AuditService audit, Clock clock) {
        this.validation = validation;
        this.computation = computation;
        this.deliveryFactory = deliveryFactory;
        this.saleFactory = saleFactory;
        this.audit = audit;
        this.clock = clock;
    }

    public synchronized void registerStore(User administrator, Store store) {
        requireRole(administrator, Role.ADMINISTRATOR);
        validation.validate(store);
        if (stores.containsKey(store.id())) throw new BusinessRuleException("Store ID already exists");
        stores.put(store.id(), store);
        products.values().forEach(p -> stocks.put(new StockKey(store.id(), p.id()), new StoreStock(store, p, 0, 0)));
        audit.record(administrator.id(), "STORE_REGISTERED", store.id());
    }

    public synchronized void registerProduct(User administrator, Product product) {
        requireRole(administrator, Role.ADMINISTRATOR);
        validation.validate(product);
        if (products.containsKey(product.id()) || products.values().stream()
                .anyMatch(p -> p.sku().strip().equalsIgnoreCase(product.sku().strip()))) {
            throw new BusinessRuleException("Product ID and SKU must be unique");
        }
        products.put(product.id(), product);
        stores.values().forEach(s -> stocks.put(new StockKey(s.id(), product.id()), new StoreStock(s, product, 0, 0)));
        audit.record(administrator.id(), "PRODUCT_REGISTERED", product.id());
    }

    public synchronized void setMinimumStock(User administrator, UUID storeId, UUID productId, int minimum) {
        requireRole(administrator, Role.ADMINISTRATOR);
        StockKey key = new StockKey(storeId, productId);
        StoreStock current = requireStock(key);
        StoreStock updated = validation.validate(new StoreStock(current.store(), current.product(), current.quantity(), minimum));
        stocks.put(key, updated);
        audit.record(administrator.id(), "MINIMUM_STOCK_CHANGED", productId);
    }

    public synchronized Delivery announceDelivery(User distributor, UUID storeId, List<ItemRequest> items) {
        requireRole(distributor, Role.DISTRIBUTOR);
        Store store = requireStore(storeId);
        validateItems(items);
        List<DeliveryLine> lines = items.stream()
                .map(i -> new DeliveryLine(requireProduct(i.productId()), i.quantity())).toList();
        Delivery delivery = deliveryFactory.create(distributor, store, lines);
        deliveries.put(delivery.id(), delivery);
        audit.record(distributor.id(), "DELIVERY_SENT", delivery.id());
        return delivery;
    }

    public synchronized Delivery acceptDelivery(User manager, UUID deliveryId) {
        Delivery delivery = requirePendingDelivery(manager, deliveryId);
        Delivery accepted = processedDelivery(delivery, manager, DeliveryStatus.ACCEPTED, null);
        Map<StockKey, StoreStock> changes = new LinkedHashMap<>();
        for (DeliveryLine line : delivery.lines()) {
            StockKey key = new StockKey(delivery.store().id(), line.product().id());
            StoreStock current = requireStock(key);
            int quantity;
            try { quantity = Math.addExact(current.quantity(), line.quantity()); }
            catch (ArithmeticException e) { throw new BusinessRuleException("Stock quantity exceeds supported range"); }
            changes.put(key, new StoreStock(current.store(), current.product(), quantity, current.minimumQuantity()));
        }
        stocks.putAll(changes);
        deliveries.put(delivery.id(), accepted);
        audit.record(manager.id(), "DELIVERY_ACCEPTED", delivery.id());
        return accepted;
    }

    public synchronized Delivery rejectDelivery(User manager, UUID deliveryId, String reason) {
        Delivery delivery = requirePendingDelivery(manager, deliveryId);
        Delivery rejected = processedDelivery(delivery, manager, DeliveryStatus.REJECTED, reason);
        deliveries.put(delivery.id(), rejected);
        audit.record(manager.id(), "DELIVERY_REJECTED", delivery.id());
        return rejected;
    }

    public synchronized Sale registerSale(User cashier, List<ItemRequest> items) {
        requireRole(cashier, Role.CASHIER);
        Store store = requireStore(cashier.storeId());
        validateItems(items);
        Map<StockKey, StoreStock> changes = new LinkedHashMap<>();
        List<SaleLine> lines = new ArrayList<>();
        for (ItemRequest item : items) {
            Product product = requireProduct(item.productId());
            StockKey key = new StockKey(store.id(), product.id());
            StoreStock current = requireStock(key);
            if (current.quantity() < item.quantity()) throw new BusinessRuleException("Insufficient stock: " + product.sku());
            changes.put(key, new StoreStock(store, product, current.quantity() - item.quantity(), current.minimumQuantity()));
            lines.add(new SaleLine(product, item.quantity(), product.price()));
        }
        Sale sale = saleFactory.create(cashier, store, lines);
        stocks.putAll(changes);
        sales.put(sale.id(), sale);
        audit.record(cashier.id(), "SALE_REGISTERED", sale.id());
        return sale;
    }

    public synchronized StoreStock stock(User actor, UUID storeId, UUID productId) {
        requireStoreAccess(actor, storeId, true);
        return requireStock(new StockKey(storeId, productId));
    }

    public synchronized List<Delivery> pendingDeliveries(User actor) {
        validation.validate(actor);
        if (actor.role() != Role.ADMINISTRATOR && actor.role() != Role.MANAGER) throw denied();
        if (actor.role() == Role.MANAGER) requireStore(actor.storeId());
        return deliveries.values().stream().filter(d -> d.status() == DeliveryStatus.SENT)
                .filter(d -> actor.role() == Role.ADMINISTRATOR || d.store().id().equals(actor.storeId())).toList();
    }

    public synchronized List<Delivery> ownDeliveries(User distributor) {
        requireRole(distributor, Role.DISTRIBUTOR);
        return deliveries.values().stream().filter(d -> d.distributor().id().equals(distributor.id())).toList();
    }

    public synchronized List<StoreStock> replenishment(User actor, UUID storeId) {
        requireStoreAccess(actor, storeId, false);
        requireStore(storeId);
        return computation.replenishmentView(stocks.values().stream()
                .filter(s -> s.store().id().equals(storeId)).toList());
    }

    private Delivery processedDelivery(Delivery original, User manager, DeliveryStatus status, String reason) {
        return validation.validate(new Delivery(original.id(), original.distributor(), original.store(),
                original.lines(), status, original.createdAt(), clock.instant(), manager, reason));
    }

    private Delivery requirePendingDelivery(User manager, UUID deliveryId) {
        requireRole(manager, Role.MANAGER);
        Delivery delivery = deliveries.get(deliveryId);
        if (delivery == null) throw new IllegalArgumentException("Delivery not found");
        if (!delivery.store().id().equals(manager.storeId())) throw denied();
        if (delivery.status() != DeliveryStatus.SENT) throw new BusinessRuleException("Delivery already processed");
        return delivery;
    }

    private void validateItems(List<ItemRequest> items) {
        if (items == null || items.isEmpty()) throw new BusinessRuleException("At least one item is required");
        items.forEach(validation::validate);
        if (items.stream().map(ItemRequest::productId).distinct().count() != items.size()) {
            throw new BusinessRuleException("A product may appear only once");
        }
    }

    private void requireRole(User actor, Role role) {
        validation.validate(actor);
        if (actor.role() != role) throw denied();
    }

    private void requireStoreAccess(User actor, UUID storeId, boolean allowCashier) {
        validation.validate(actor);
        if (actor.role() == Role.ADMINISTRATOR) return;
        if ((actor.role() == Role.MANAGER || (allowCashier && actor.role() == Role.CASHIER))
                && Objects.equals(storeId, actor.storeId())) return;
        throw denied();
    }

    private static SecurityException denied() { return new SecurityException("Action is not permitted for this role/store"); }

    private Store requireStore(UUID id) {
        Store store = stores.get(id);
        if (store == null) throw new IllegalArgumentException("Store not found");
        return store;
    }

    private Product requireProduct(UUID id) {
        Product product = products.get(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        return product;
    }

    private StoreStock requireStock(StockKey key) {
        StoreStock stock = stocks.get(key);
        if (stock == null) throw new IllegalArgumentException("Store/product stock not found");
        return stock;
    }
}
