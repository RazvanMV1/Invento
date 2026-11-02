package ro.invento.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Sale(@NotNull UUID id, @NotNull @Valid Store store, @NotNull @Valid User cashier,
                   @NotEmpty List<@NotNull @Valid SaleLine> lines, @NotNull Instant createdAt) {
    public Sale {
        if (lines != null) lines = List.copyOf(lines);
    }

    @AssertTrue(message = "Cashier must belong to the sale store")
    public boolean isCashierValid() {
        return cashier == null || store == null || (cashier.role() == Role.CASHIER
                && java.util.Objects.equals(store.id(), cashier.storeId()));
    }

    @AssertTrue(message = "A product may appear only once in a sale")
    public boolean isProductListUnique() {
        if (lines == null) return true;
        var ids = lines.stream().filter(l -> l.product() != null).map(l -> l.product().id()).toList();
        return ids.stream().distinct().count() == ids.size();
    }
}
