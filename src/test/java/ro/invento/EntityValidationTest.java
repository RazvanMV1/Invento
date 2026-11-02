package ro.invento;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ro.invento.domain.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class EntityValidationTest extends DomainTestSupport {
    static Stream<Arguments> invalidEntities() {
        UUID id = UUID.randomUUID();
        Product product = new Product(id, "P1", "Produs", Money.of("1.00"));
        Store store = new Store(id, "Magazin", "Adresa");
        return Stream.of(
            Arguments.of(new Product(null, "P1", "Produs", Money.of("1.00")), "id"),
            Arguments.of(new Product(id, " ", "Produs", Money.of("1.00")), "sku"),
            Arguments.of(new Product(id, "P1", "", Money.of("1.00")), "name"),
            Arguments.of(new Product(id, "P1", "Produs", null), "price"),
            Arguments.of(new Product(id, "P1", "Produs", Money.of("0.00")), "pricePositive"),
            Arguments.of(new Product(id, "P1", "Produs", Money.of("-1.00")), "price"),
            Arguments.of(new Money(null), "amount"),
            Arguments.of(Money.of("-0.01"), "amount"),
            Arguments.of(Money.of("1.001"), "amount"),
            Arguments.of(Money.of("10000000000.00"), "amount"),
            Arguments.of(new Store(null, "Magazin", "Adresa"), "id"),
            Arguments.of(new Store(id, " ", "Adresa"), "name"),
            Arguments.of(new Store(id, "Magazin", ""), "address"),
            Arguments.of(new User(null, "User", Role.ADMINISTRATOR, null), "id"),
            Arguments.of(new User(id, " ", Role.ADMINISTRATOR, null), "name"),
            Arguments.of(new User(id, "User", null, null), "role"),
            Arguments.of(new User(id, "User", Role.MANAGER, null), "storeAssignmentValid"),
            Arguments.of(new User(id, "User", Role.CASHIER, null), "storeAssignmentValid"),
            Arguments.of(new User(id, "User", Role.DISTRIBUTOR, id), "storeAssignmentValid"),
            Arguments.of(new ItemRequest(null, 1), "productId"),
            Arguments.of(new ItemRequest(id, 0), "quantity"),
            Arguments.of(new DeliveryLine(product, -1), "quantity"),
            Arguments.of(new DeliveryLine(null, 1), "product"),
            Arguments.of(new SaleLine(product, 0, Money.of("1.00")), "quantity"),
            Arguments.of(new SaleLine(product, 1, null), "unitPrice"),
            Arguments.of(new StoreStock(store, product, -1, 0), "quantity"),
            Arguments.of(new StoreStock(store, product, 1, -1), "minimumQuantity")
        );
    }

    @ParameterizedTest(name = "invalid entity #{index}: property {1}")
    @MethodSource("invalidEntities")
    void rejectsInvalidEntityFields(Object entity, String property) {
        assertThatThrownBy(() -> validation.validate(entity))
                .isInstanceOfSatisfying(ConstraintViolationException.class, e ->
                    assertThat(e.getConstraintViolations()).anySatisfy(v ->
                        assertThat(v.getPropertyPath().toString()).contains(property)));
    }

    @Test
    void acceptsValidEntitiesAndZeroStock() {
        assertThat(validation.validate(coffee)).isSameAs(coffee);
        assertThatCode(() -> validation.validate(new StoreStock(storeA, coffee, 0, 0))).doesNotThrowAnyException();
        assertThatCode(() -> validation.validate(managerA)).doesNotThrowAnyException();
        assertThatCode(() -> validation.validate(Money.of("0.00"))).doesNotThrowAnyException();
    }

    @Test
    void validatesNestedProductInsideDeliveryLine() {
        Product invalid = new Product(coffee.id(), "", coffee.name(), coffee.price());
        assertThatThrownBy(() -> validation.validate(new DeliveryLine(invalid, 1)))
                .isInstanceOfSatisfying(ConstraintViolationException.class, e ->
                        assertThat(e.getConstraintViolations()).anySatisfy(v ->
                                assertThat(v.getPropertyPath().toString()).isEqualTo("product.sku")));
    }

    @Test
    void validatesEachLineInsideAggregate() {
        Sale sale = new Sale(UUID.randomUUID(), storeA, cashierA,
                List.of(new SaleLine(coffee, -1, coffee.price())), clock.instant());
        assertThatThrownBy(() -> validation.validate(sale)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsProcessedDeliveryWithoutManagerAndTimestamp() {
        Delivery delivery = new Delivery(UUID.randomUUID(), distributor, storeA,
                List.of(new DeliveryLine(coffee, 1)), DeliveryStatus.ACCEPTED, clock.instant(), null, null, null);
        assertThatThrownBy(() -> validation.validate(delivery)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsNullObject() {
        assertThatNullPointerException().isThrownBy(() -> validation.validate(null));
    }
}
