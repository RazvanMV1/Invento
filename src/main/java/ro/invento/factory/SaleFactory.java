package ro.invento.factory;

import ro.invento.domain.*;
import java.util.List;

public interface SaleFactory {
    Sale create(User cashier, Store store, List<SaleLine> lines);
}
