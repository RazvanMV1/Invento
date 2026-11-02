package ro.invento.service;

import ro.invento.domain.*;
import java.util.Collection;
import java.util.List;

public interface ComputationService {
    Money lineTotal(SaleLine line);
    Money saleTotal(Sale sale);
    List<StoreStock> replenishmentView(Collection<StoreStock> stocks);
}
