package lat.vmdev.inventory.stockmovement;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes warehouse movements. A failure the handler marks
 * {@link NonRetryableMovementException} skips retries and is dead-lettered
 * immediately; anything else is forwarded to a delayed retry topic with
 * exponential backoff, and lands on the {@code -dlt} topic once attempts are
 * exhausted. The main partition never blocks on a retry.
 */
@Component
public class StockMovementConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockMovementConsumer.class);

    private final StockMovementHandler handler;
    private final DeadLetterService deadLetters;

    public StockMovementConsumer(StockMovementHandler handler, DeadLetterService deadLetters) {
        this.handler = handler;
        this.deadLetters = deadLetters;
    }

    @RetryableTopic(
            attempts = "${inventory.kafka.retry.attempts}",
            backoff = @Backoff(
                    delayExpression = "${inventory.kafka.retry.initial-interval-ms}",
                    multiplierExpression = "${inventory.kafka.retry.multiplier}",
                    maxDelayExpression = "${inventory.kafka.retry.max-interval-ms}"),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = NonRetryableMovementException.class)
    @KafkaListener(topics = "${inventory.kafka.topics.stock-movements}", groupId = "inventory")
    public void onMovement(
            @Payload StockMovementEvent event,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.debug("movement {} on {} (key={})", event.eventId(), topic, key);
        handler.handle(event);
    }

    @DltHandler
    public void onDeadLetter(
            ConsumerRecord<String, StockMovementEvent> record,
            @Header(name = KafkaHeaders.EXCEPTION_FQCN, required = false) String exceptionType,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) String stackTrace) {
        deadLetters.record(record, exceptionType, exceptionMessage, stackTrace);
    }
}
