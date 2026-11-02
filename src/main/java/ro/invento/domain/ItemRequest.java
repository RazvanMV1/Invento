package ro.invento.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ItemRequest(@NotNull UUID productId, @Positive int quantity) { }
