package lat.vmdev.inventory.stockmovement;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lat.vmdev.inventory.domain.StockRuleViolationException;
import lat.vmdev.inventory.inbox.IdempotentInbox;
import lat.vmdev.inventory.stock.StockService;
import lat.vmdev.inventory.support.FailureClass;
import lat.vmdev.inventory.support.PersistenceExceptionClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Applies one warehouse movement, exactly once, and decides whether a failure
 * should be retried or dead-lettered.
 */
@Component
public class StockMovementHandler {

    private static final Logger log = LoggerFactory.getLogger(StockMovementHandler.class);

    private final StockService stock;
    private final IdempotentInbox inbox;
    private final PersistenceExceptionClassifier classifier;
    private final Timer processingTimer;

    public StockMovementHandler(
            StockService stock,
            IdempotentInbox inbox,
            PersistenceExceptionClassifier classifier,
            MeterRegistry meters) {
        this.stock = stock;
        this.inbox = inbox;
        this.classifier = classifier;
        this.processingTimer = Timer.builder("inventory.stock_movement.processing")
                .description("time to apply one warehouse movement")
                .publishPercentileHistogram()
                .register(meters);
    }

    public void handle(StockMovementEvent event) {
        try {
            processingTimer.record(() ->
                    inbox.runOnce(event.eventId(), "StockMovementEvent", () -> apply(event)));
        } catch (StockRuleViolationException | DataIntegrityViolationException permanent) {
            throw new NonRetryableMovementException(
                    "permanent failure applying movement " + event.eventId(), permanent);
        } catch (RuntimeException e) {
            if (classifier.classify(e) == FailureClass.PERMANENT) {
                throw new NonRetryableMovementException(
                        "permanent failure applying movement " + event.eventId(), e);
            }
            log.warn("transient failure applying movement {}, will retry: {}", event.eventId(), e.toString());
            throw e;
        }
    }

    private void apply(StockMovementEvent event) {
        String correlationId = "evt:" + event.eventId();
        switch (event.type()) {
            case RECEIPT -> stock.receive(event.sku(), event.quantity(), correlationId);
            case ADJUSTMENT -> stock.adjust(event.sku(), event.quantity(), correlationId);
        }
    }
}
