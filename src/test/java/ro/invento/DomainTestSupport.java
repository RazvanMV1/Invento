package ro.invento;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import ro.invento.domain.*;
import ro.invento.factory.*;
import ro.invento.service.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

abstract class DomainTestSupport {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    final ValidationService validation = new BeanValidationService(validatorFactory.getValidator());
    final ComputationService computation = new DefaultComputationService(validation);
    final DeliveryFactory deliveryFactory = new DefaultDeliveryFactory(validation, clock);
    final SaleFactory saleFactory = new DefaultSaleFactory(validation, clock);
    final AuditService audit = new InMemoryAuditService(clock);
    final WorkflowService workflow = new InMemoryWorkflowService(validation, computation, deliveryFactory, saleFactory, audit, clock);

    final Store storeA = new Store(UUID.randomUUID(), "Magazin A", "Strada A 1");
    final Store storeB = new Store(UUID.randomUUID(), "Magazin B", "Strada B 2");
    final Product coffee = new Product(UUID.randomUUID(), "CAF-001", "Cafea", Money.of("25.50"));
    final Product tea = new Product(UUID.randomUUID(), "TEA-001", "Ceai", Money.of("18.00"));
    final User administrator = new User(UUID.randomUUID(), "Admin", Role.ADMINISTRATOR, null);
    final User distributor = new User(UUID.randomUUID(), "Distribuitor", Role.DISTRIBUTOR, null);
    final User managerA = new User(UUID.randomUUID(), "Gestionar A", Role.MANAGER, storeA.id());
    final User managerB = new User(UUID.randomUUID(), "Gestionar B", Role.MANAGER, storeB.id());
    final User cashierA = new User(UUID.randomUUID(), "Casier A", Role.CASHIER, storeA.id());
    final User cashierB = new User(UUID.randomUUID(), "Casier B", Role.CASHIER, storeB.id());

    List<ItemRequest> coffeeItems(int quantity) { return List.of(new ItemRequest(coffee.id(), quantity)); }

    @AfterEach
    void closeValidator() { validatorFactory.close(); }
}
