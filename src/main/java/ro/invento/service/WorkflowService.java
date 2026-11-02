package ro.invento.service;

import ro.invento.domain.*;
import java.util.List;
import java.util.UUID;

public interface WorkflowService {
    void registerStore(User administrator, Store store);
    void registerProduct(User administrator, Product product);
    void setMinimumStock(User administrator, UUID storeId, UUID productId, int minimum);
    Delivery announceDelivery(User distributor, UUID storeId, List<ItemRequest> items);
    Delivery acceptDelivery(User manager, UUID deliveryId);
    Delivery rejectDelivery(User manager, UUID deliveryId, String reason);
    Sale registerSale(User cashier, List<ItemRequest> items);
    StoreStock stock(User actor, UUID storeId, UUID productId);
    List<Delivery> pendingDeliveries(User actor);
    List<Delivery> ownDeliveries(User distributor);
    List<StoreStock> replenishment(User actor, UUID storeId);
}
