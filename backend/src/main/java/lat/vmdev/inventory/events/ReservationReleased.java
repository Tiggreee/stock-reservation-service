package lat.vmdev.inventory.events;

import java.time.Instant;
import java.util.UUID;

public record ReservationReleased(UUID reservationId, String sku, int quantity, String reason, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String aggregateId() {
        return reservationId.toString();
    }
}
