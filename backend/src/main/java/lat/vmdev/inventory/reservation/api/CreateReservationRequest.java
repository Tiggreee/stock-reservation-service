package lat.vmdev.inventory.reservation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReservationRequest(
        @NotBlank @Size(max = 64) String sku,
        @Positive int quantity,
        @NotBlank @Size(max = 100) String idempotencyKey) {}
