package ro.invento.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Delivery(
        @NotNull UUID id, @NotNull @Valid User distributor, @NotNull @Valid Store store,
        @NotEmpty List<@NotNull @Valid DeliveryLine> lines, @NotNull DeliveryStatus status,
        @NotNull Instant createdAt, Instant processedAt, @Valid User processedBy, String rejectionReason) {

    public Delivery {
        if (lines != null) lines = List.copyOf(lines);
    }

    @AssertTrue(message = "Delivery must be announced by a distributor")
    public boolean isDistributorValid() {
        return distributor == null || distributor.role() == Role.DISTRIBUTOR;
    }

    @AssertTrue(message = "A product may appear only once in a delivery")
    public boolean isProductListUnique() {
        if (lines == null) return true;
        var ids = lines.stream().filter(l -> l.product() != null).map(l -> l.product().id()).toList();
        return ids.stream().distinct().count() == ids.size();
    }

    @AssertTrue(message = "Delivery processing data must match its status and destination")
    public boolean isProcessingConsistent() {
        if (status == null) return true;
        if (status == DeliveryStatus.SENT) {
            return processedAt == null && processedBy == null && rejectionReason == null;
        }
        boolean validManager = processedBy != null && processedBy.role() == Role.MANAGER
                && store != null && java.util.Objects.equals(store.id(), processedBy.storeId());
        boolean validTime = processedAt != null && createdAt != null && !processedAt.isBefore(createdAt);
        boolean validReason = status == DeliveryStatus.REJECTED
                ? rejectionReason != null && !rejectionReason.isBlank() : rejectionReason == null;
        return validManager && validTime && validReason;
    }
}
