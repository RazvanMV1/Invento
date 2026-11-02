package ro.invento.domain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record User(@NotNull UUID id, @NotBlank String name, @NotNull Role role, UUID storeId) {

    @AssertTrue(message = "Only managers and cashiers must be assigned to a store")
    public boolean isStoreAssignmentValid() {
        if (role == null) return true;
        boolean employee = role == Role.MANAGER || role == Role.CASHIER;
        return employee == (storeId != null);
    }
}
