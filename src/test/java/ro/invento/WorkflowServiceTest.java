package ro.invento;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ro.invento.domain.*;
import ro.invento.service.BusinessRuleException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class WorkflowServiceTest extends DomainTestSupport {
    @BeforeEach
    void registerCatalog() {
        workflow.registerStore(administrator, storeA);
        workflow.registerStore(administrator, storeB);
        workflow.registerProduct(administrator, coffee);
        workflow.registerProduct(administrator, tea);
    }

    private Delivery deliver(int quantity) {
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(), coffeeItems(quantity));
        return workflow.acceptDelivery(managerA, delivery.id());
    }

    private int coffeeStock() { return workflow.stock(administrator, storeA.id(), coffee.id()).quantity(); }

    @Test
    void announcingDoesNotChangeStockAndNotifiesOnlyRelevantUsers() {
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(), coffeeItems(10));
        assertThat(coffeeStock()).isZero();
        assertThat(workflow.pendingDeliveries(administrator)).containsExactly(delivery);
        assertThat(workflow.pendingDeliveries(managerA)).containsExactly(delivery);
        assertThat(workflow.pendingDeliveries(managerB)).isEmpty();
        assertThat(workflow.ownDeliveries(distributor)).containsExactly(delivery);
        User other = new User(UUID.randomUUID(), "Alt distribuitor", Role.DISTRIBUTOR, null);
        assertThat(workflow.ownDeliveries(other)).isEmpty();
    }

    @Test
    void acceptingAddsAllLinesOnlyToDestinationAndRemovesPendingNotification() {
        Delivery original = workflow.announceDelivery(distributor, storeA.id(),
                List.of(new ItemRequest(coffee.id(), 10), new ItemRequest(tea.id(), 3)));
        Delivery accepted = workflow.acceptDelivery(managerA, original.id());
        assertThat(accepted.status()).isEqualTo(DeliveryStatus.ACCEPTED);
        assertThat(accepted.processedBy()).isEqualTo(managerA);
        assertThat(accepted.processedAt()).isEqualTo(clock.instant());
        assertThat(original.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(coffeeStock()).isEqualTo(10);
        assertThat(workflow.stock(managerA, storeA.id(), tea.id()).quantity()).isEqualTo(3);
        assertThat(workflow.stock(administrator, storeB.id(), coffee.id()).quantity()).isZero();
        assertThat(workflow.pendingDeliveries(managerA)).isEmpty();
    }

    @Test
    void rejectingKeepsStockAndRecordsReason() {
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(), coffeeItems(10));
        Delivery rejected = workflow.rejectDelivery(managerA, delivery.id(), "Marfa deteriorata");
        assertThat(rejected.status()).isEqualTo(DeliveryStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Marfa deteriorata");
        assertThat(coffeeStock()).isZero();
    }

    @Test
    void rejectionWithoutReasonLeavesDeliveryPending() {
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(), coffeeItems(10));
        assertThatThrownBy(() -> workflow.rejectDelivery(managerA, delivery.id(), " "))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(workflow.pendingDeliveries(managerA)).containsExactly(delivery);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"ACCEPTED", "REJECTED"})
    void terminalDeliveryCannotBeAcceptedOrRejectedAgain(DeliveryStatus status) {
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(), coffeeItems(10));
        if (status == DeliveryStatus.ACCEPTED) workflow.acceptDelivery(managerA, delivery.id());
        else workflow.rejectDelivery(managerA, delivery.id(), "Neconform");
        int before = coffeeStock();
        assertThatThrownBy(() -> workflow.acceptDelivery(managerA, delivery.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> workflow.rejectDelivery(managerA, delivery.id(), "Alt motiv")).isInstanceOf(BusinessRuleException.class);
        assertThat(coffeeStock()).isEqualTo(before);
    }

    @Test
    void onlyDestinationManagerCanAcceptDelivery() {
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(), coffeeItems(10));
        assertThatThrownBy(() -> workflow.acceptDelivery(managerB, delivery.id())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflow.acceptDelivery(administrator, delivery.id())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflow.acceptDelivery(cashierA, delivery.id())).isInstanceOf(SecurityException.class);
        assertThat(coffeeStock()).isZero();
    }

    @Test
    void saleUsesStoreStockAndKeepsUnitPrices() {
        deliver(10);
        Sale sale = workflow.registerSale(cashierA, coffeeItems(2));
        assertThat(coffeeStock()).isEqualTo(8);
        assertThat(sale.cashier()).isEqualTo(cashierA);
        assertThat(sale.store()).isEqualTo(storeA);
        assertThat(computation.saleTotal(sale).amount()).isEqualByComparingTo("51.00");
    }

    @Test
    void insufficientSecondProductRollsBackEntireSaleAndDoesNotAuditSuccess() {
        deliver(10);
        int eventsBefore = audit.events().size();
        assertThatThrownBy(() -> workflow.registerSale(cashierA,
                List.of(new ItemRequest(coffee.id(), 2), new ItemRequest(tea.id(), 1))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(coffeeStock()).isEqualTo(10);
        assertThat(audit.events()).hasSize(eventsBefore);
    }

    @Test
    void cashierCannotSellFromAnotherStoreStock() {
        deliver(10);
        assertThatThrownBy(() -> workflow.registerSale(cashierB, coffeeItems(1)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(coffeeStock()).isEqualTo(10);
    }

    @Test
    void duplicateItemsCannotBypassAvailableQuantity() {
        deliver(5);
        assertThatThrownBy(() -> workflow.registerSale(cashierA,
                List.of(new ItemRequest(coffee.id(), 4), new ItemRequest(coffee.id(), 4))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(coffeeStock()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidQuantitiesBeforeChangingStock() {
        deliver(5);
        assertThatThrownBy(() -> workflow.registerSale(cashierA, coffeeItems(-1)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(coffeeStock()).isEqualTo(5);
    }

    @Test
    void unknownProductPreventsEntireSale() {
        deliver(5);
        assertThatThrownBy(() -> workflow.registerSale(cashierA,
                List.of(new ItemRequest(coffee.id(), 1), new ItemRequest(UUID.randomUUID(), 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(coffeeStock()).isEqualTo(5);
    }

    @Test
    void emptySaleAndDeliveryAreRejected() {
        assertThatThrownBy(() -> workflow.registerSale(cashierA, List.of())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> workflow.announceDelivery(distributor, storeA.id(), List.of()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cashierCanReadOnlyOwnStoreAndCannotReadManagerReports() {
        assertThat(workflow.stock(cashierA, storeA.id(), coffee.id()).quantity()).isZero();
        assertThatThrownBy(() -> workflow.stock(cashierA, storeB.id(), coffee.id())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflow.replenishment(cashierA, storeA.id())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflow.stock(distributor, storeA.id(), coffee.id())).isInstanceOf(SecurityException.class);
    }

    @Test
    void nonAdministratorCannotRegisterCatalog() {
        assertThatThrownBy(() -> workflow.registerProduct(distributor, coffee)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflow.registerStore(managerA, storeA)).isInstanceOf(SecurityException.class);
    }

    @Test
    void skuIsUniqueIgnoringCaseAndOuterWhitespace() {
        Product duplicate = new Product(UUID.randomUUID(), " caf-001 ", "Alt produs", Money.of("2.00"));
        assertThatThrownBy(() -> workflow.registerProduct(administrator, duplicate)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void storeAndProductIdsCannotBeOverwritten() {
        assertThatThrownBy(() -> workflow.registerStore(administrator, storeA)).isInstanceOf(BusinessRuleException.class);
        Product duplicate = new Product(coffee.id(), "OTHER", "Alt produs", coffee.price());
        assertThatThrownBy(() -> workflow.registerProduct(administrator, duplicate)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void replenishmentUsesEachStoresOwnThreshold() {
        deliver(4);
        workflow.setMinimumStock(administrator, storeA.id(), coffee.id(), 4);
        workflow.setMinimumStock(administrator, storeB.id(), coffee.id(), 0);
        assertThat(workflow.replenishment(managerA, storeA.id())).anySatisfy(s -> {
            assertThat(s.product()).isEqualTo(coffee);
            assertThat(s.quantity()).isEqualTo(4);
        });
        assertThatThrownBy(() -> workflow.replenishment(managerB, storeA.id())).isInstanceOf(SecurityException.class);
    }

    @Test
    void negativeMinimumIsRejectedWithoutChangingPreviousThreshold() {
        workflow.setMinimumStock(administrator, storeA.id(), coffee.id(), 4);
        assertThatThrownBy(() -> workflow.setMinimumStock(administrator, storeA.id(), coffee.id(), -1))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(workflow.stock(administrator, storeA.id(), coffee.id()).minimumQuantity()).isEqualTo(4);
    }

    @Test
    void stockOverflowRollsBackAllDeliveryLines() {
        deliver(Integer.MAX_VALUE);
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(),
                List.of(new ItemRequest(tea.id(), 2), new ItemRequest(coffee.id(), 1)));
        assertThatThrownBy(() -> workflow.acceptDelivery(managerA, delivery.id())).isInstanceOf(BusinessRuleException.class);
        assertThat(workflow.stock(administrator, storeA.id(), tea.id()).quantity()).isZero();
        assertThat(workflow.pendingDeliveries(managerA)).containsExactly(delivery);
    }

    @Test
    void simultaneousSalesCannotOversellLastUnit() throws Exception {
        deliver(1);
        int succeeded = runConcurrent(() -> workflow.registerSale(cashierA, coffeeItems(1)));
        assertThat(succeeded).isEqualTo(1);
        assertThat(coffeeStock()).isZero();
        assertThat(audit.events().stream().filter(e -> e.action().equals("SALE_REGISTERED"))).hasSize(1);
    }

    @Test
    void simultaneousAcceptsCannotDuplicateStock() throws Exception {
        Delivery delivery = workflow.announceDelivery(distributor, storeA.id(), coffeeItems(5));
        int succeeded = runConcurrent(() -> workflow.acceptDelivery(managerA, delivery.id()));
        assertThat(succeeded).isEqualTo(1);
        assertThat(coffeeStock()).isEqualTo(5);
    }

    private int runConcurrent(Runnable operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> task = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent test did not start");
                try { operation.run(); succeeded.incrementAndGet(); }
                catch (BusinessRuleException expectedConflict) { }
                return null;
            };
            Future<Void> first = pool.submit(task);
            Future<Void> second = pool.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            return succeeded.get();
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
