package lat.vmdev.inventory.events;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreated(UUID reservationId, String sku, int quantity, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String aggregateId() {
        return reservationId.toString();
    }
}
