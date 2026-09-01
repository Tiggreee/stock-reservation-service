package lat.vmdev.inventory.inbox;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work exactly once for a given event id. The "processed" marker
 * and the work commit together: a duplicate delivery is a clean no-op, and a
 * failed application leaves no marker behind, so a retry gets a fresh attempt.
 */
@Component
public class IdempotentInbox {

    private static final Logger log = LoggerFactory.getLogger(IdempotentInbox.class);

    private final InboxRepository inbox;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IdempotentInbox(InboxRepository inbox, TransactionTemplate tx, Clock clock) {
        this.inbox = inbox;
        this.tx = tx;
        this.clock = clock;
    }

    public void runOnce(String eventId, String eventType, Runnable work) {
        if (inbox.existsById(eventId)) {
            log.debug("event {} already applied, skipping", eventId);
            return;
        }
        try {
            tx.executeWithoutResult(status -> {
                inbox.saveAndFlush(new InboxEvent(eventId, eventType, clock.instant()));
                work.run();
            });
        } catch (DataIntegrityViolationException e) {
            if (inbox.existsById(eventId)) {
                log.debug("event {} applied concurrently, skipping", eventId);
                return;
            }
            throw e; // the violation came from the work itself — a permanent failure
        }
    }
}
