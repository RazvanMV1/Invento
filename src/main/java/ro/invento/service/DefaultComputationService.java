package ro.invento.service;

import ro.invento.domain.*;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public final class DefaultComputationService implements ComputationService {
    private final ValidationService validation;

    public DefaultComputationService(ValidationService validation) {
        this.validation = validation;
    }

    public Money lineTotal(SaleLine line) {
        validation.validate(line);
        return new Money(line.unitPrice().amount().multiply(BigDecimal.valueOf(line.quantity())));
    }

    public Money saleTotal(Sale sale) {
        validation.validate(sale);
        return new Money(sale.lines().stream().map(this::lineTotal).map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public List<StoreStock> replenishmentView(Collection<StoreStock> stocks) {
        stocks.forEach(validation::validate);
        return stocks.stream().filter(s -> s.quantity() <= s.minimumQuantity()).toList();
    }
}
