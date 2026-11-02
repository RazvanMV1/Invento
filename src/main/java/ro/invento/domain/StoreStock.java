package ro.invento.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StoreStock(@NotNull @Valid Store store, @NotNull @Valid Product product,
                         @PositiveOrZero int quantity, @PositiveOrZero int minimumQuantity) { }
