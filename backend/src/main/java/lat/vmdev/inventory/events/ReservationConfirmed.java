package lat.vmdev.inventory.events;

import java.time.Instant;
import java.util.UUID;

public record ReservationConfirmed(UUID reservationId, String sku, int quantity, String orderRef, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String aggregateId() {
        return reservationId.toString();
    }
}
