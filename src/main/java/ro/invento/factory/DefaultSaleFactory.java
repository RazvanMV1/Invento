package ro.invento.factory;

import ro.invento.domain.*;
import ro.invento.service.ValidationService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class DefaultSaleFactory implements SaleFactory {
    private final ValidationService validation;
    private final Clock clock;

    public DefaultSaleFactory(ValidationService validation, Clock clock) {
        this.validation = validation;
        this.clock = clock;
    }

    public Sale create(User cashier, Store store, List<SaleLine> lines) {
        return validation.validate(new Sale(UUID.randomUUID(), store, cashier, lines, clock.instant()));
    }
}
