package lat.vmdev.inventory.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import lat.vmdev.inventory.events.DomainEvent;
import lat.vmdev.inventory.events.ReservationConfirmed;
import lat.vmdev.inventory.events.ReservationCreated;
import lat.vmdev.inventory.events.ReservationReleased;
import lat.vmdev.inventory.events.StockLevelChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns an in-process {@link DomainEvent} into an {@link OutboxEvent} row just
 * before the producing transaction commits, so the two are atomic.
 */
@Component
public class DomainEventOutboxWriter {

    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public DomainEventOutboxWriter(OutboxRepository outbox, ObjectMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void stage(DomainEvent event) {
        try {
            outbox.save(new OutboxEvent(
                    aggregateType(event),
                    event.aggregateId(),
                    event.getClass().getSimpleName(),
                    json.writeValueAsString(event),
                    clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise domain event " + event, e);
        }
    }

    private static String aggregateType(DomainEvent event) {
        return switch (event) {
            case ReservationCreated ignored -> "reservation";
            case ReservationConfirmed ignored -> "reservation";
            case ReservationReleased ignored -> "reservation";
            case StockLevelChanged ignored -> "stock";
        };
    }
}
