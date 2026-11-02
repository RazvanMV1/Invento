package ro.invento.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaleLine(@NotNull @Valid Product product, @Positive int quantity,
                       @NotNull @Valid Money unitPrice) { }
