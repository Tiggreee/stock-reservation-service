package lat.vmdev.inventory.reservation.api;

import java.time.Instant;
import java.util.UUID;
import lat.vmdev.inventory.domain.Reservation;

public record ReservationView(
        UUID id,
        String sku,
        int quantity,
        String status,
        String idempotencyKey,
        String orderRef,
        Instant createdAt,
        Instant expiresAt,
        Instant settledAt) {

    public static ReservationView of(Reservation r) {
        return new ReservationView(
                r.getId(),
                r.getSku(),
                r.getQuantity(),
                r.getStatus().name(),
                r.getIdempotencyKey(),
                r.getOrderRef(),
                r.getCreatedAt(),
                r.getExpiresAt(),
                r.getSettledAt());
    }
}
