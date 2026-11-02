package ro.invento.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record Store(@NotNull UUID id, @NotBlank String name, @NotBlank String address) { }
