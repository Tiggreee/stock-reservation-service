package lat.vmdev.inventory.events;

import java.time.Instant;

/**
 * Marker for events the service raises about its own state changes. They are
 * published in-process on the committing transaction and forwarded to Kafka by
 * the transactional outbox.
 */
public sealed interface DomainEvent
        permits ReservationCreated, ReservationConfirmed, ReservationReleased, StockLevelChanged {

    String aggregateId();

    Instant occurredAt();
}
