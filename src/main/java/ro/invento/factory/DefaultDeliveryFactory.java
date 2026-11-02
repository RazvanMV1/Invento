package ro.invento.factory;

import ro.invento.domain.*;
import ro.invento.service.ValidationService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class DefaultDeliveryFactory implements DeliveryFactory {
    private final ValidationService validation;
    private final Clock clock;

    public DefaultDeliveryFactory(ValidationService validation, Clock clock) {
        this.validation = validation;
        this.clock = clock;
    }

    public Delivery create(User distributor, Store store, List<DeliveryLine> lines) {
        return validation.validate(new Delivery(UUID.randomUUID(), distributor, store, lines,
                DeliveryStatus.SENT, clock.instant(), null, null, null));
    }
}
