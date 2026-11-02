package ro.invento.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record Product(@NotNull UUID id, @NotBlank String sku, @NotBlank String name,
                      @NotNull @Valid Money price) {

    @AssertTrue(message = "Product price must be positive")
    public boolean isPricePositive() {
        return price == null || price.amount() == null || price.amount().signum() > 0;
    }
}
