package ro.invento;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import ro.invento.domain.*;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AggregateFactoryTest extends DomainTestSupport {
    @Test
    void deliveryStartsSentWithCorrectAssociationsAndTime() {
        Delivery delivery = deliveryFactory.create(distributor, storeA, List.of(new DeliveryLine(coffee, 4)));
        assertThat(delivery.id()).isNotNull();
        assertThat(delivery.distributor()).isEqualTo(distributor);
        assertThat(delivery.store()).isEqualTo(storeA);
        assertThat(delivery.lines().getFirst().product()).isEqualTo(coffee);
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.createdAt()).isEqualTo(clock.instant());
        assertThat(delivery.processedAt()).isNull();
    }

    @Test
    void aggregateMakesDefensiveCopyOfLines() {
        List<DeliveryLine> input = new ArrayList<>(List.of(new DeliveryLine(coffee, 4)));
        Delivery delivery = deliveryFactory.create(distributor, storeA, input);
        input.clear();
        assertThat(delivery.lines()).hasSize(1);
        assertThatThrownBy(() -> delivery.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deliveryRejectsEmptyLines() {
        assertThatThrownBy(() -> deliveryFactory.create(distributor, storeA, List.of()))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void deliveryRejectsDuplicateProducts() {
        assertThatThrownBy(() -> deliveryFactory.create(distributor, storeA,
                List.of(new DeliveryLine(coffee, 1), new DeliveryLine(coffee, 2))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void deliveryRejectsWrongActorRole() {
        assertThatThrownBy(() -> deliveryFactory.create(cashierA, storeA, List.of(new DeliveryLine(coffee, 1))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void salePreservesPriceSnapshotAndImmutableLines() {
        Sale sale = saleFactory.create(cashierA, storeA, List.of(new SaleLine(coffee, 2, Money.of("20.00"))));
        assertThat(sale.lines().getFirst().unitPrice().amount()).isEqualByComparingTo("20.00");
        assertThat(coffee.price().amount()).isEqualByComparingTo("25.50");
        assertThatThrownBy(() -> sale.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void saleRejectsCashierFromAnotherStore() {
        assertThatThrownBy(() -> saleFactory.create(cashierB, storeA, List.of(new SaleLine(coffee, 1, coffee.price()))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void saleRejectsDuplicateProducts() {
        SaleLine line = new SaleLine(coffee, 1, coffee.price());
        assertThatThrownBy(() -> saleFactory.create(cashierA, storeA, List.of(line, line)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void saleRejectsEmptyAggregate() {
        assertThatThrownBy(() -> saleFactory.create(cashierA, storeA, List.of()))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void differentAggregatesReceiveDifferentIds() {
        var lines = List.of(new DeliveryLine(coffee, 1));
        assertThat(deliveryFactory.create(distributor, storeA, lines).id())
                .isNotEqualTo(deliveryFactory.create(distributor, storeA, lines).id());
    }
}
