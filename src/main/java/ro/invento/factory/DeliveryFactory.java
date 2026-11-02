package ro.invento.factory;

import ro.invento.domain.*;
import java.util.List;

public interface DeliveryFactory {
    Delivery create(User distributor, Store store, List<DeliveryLine> lines);
}
