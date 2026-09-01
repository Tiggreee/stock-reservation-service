package lat.vmdev.inventory.stockmovement;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lat.vmdev.inventory.config.InventoryProperties;
import lat.vmdev.inventory.observability.InventoryMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists dead letters and republishes them to the main topic on request. */
@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final DeadLetterRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;
    private final Clock clock;
    private final InventoryProperties props;
    private final InventoryMetrics metrics;

    public DeadLetterService(
            DeadLetterRepository repository,
            KafkaTemplate<String, Object> kafka,
            ObjectMapper json,
            Clock clock,
            InventoryProperties props,
            InventoryMetrics metrics) {
        this.repository = repository;
        this.kafka = kafka;
        this.json = json;
        this.clock = clock;
        this.props = props;
        this.metrics = metrics;
    }

    private static final int MAX_DETAIL = 4_000;

    @Transactional
    public void record(
            ConsumerRecord<String, ?> record,
            String exceptionType,
            String exceptionMessage,
            String stackTrace) {
        String payload;
        try {
            payload = json.writeValueAsString(record.value());
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            payload = String.valueOf(record.value());
        }
        String detail = exceptionMessage == null ? "" : exceptionMessage;
        if (stackTrace != null) {
            detail = (detail.isBlank() ? "" : detail + "\n") + stackTrace;
        }
        if (detail.length() > MAX_DETAIL) {
            detail = detail.substring(0, MAX_DETAIL);
        }
        repository.save(new DeadLetter(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                payload,
                exceptionType,
                detail.isBlank() ? null : detail,
                clock.instant()));
        metrics.movementDeadLettered();
        log.error("dead-lettered {}#{}@{} key={} cause={} {}",
                record.topic(), record.partition(), record.offset(), record.key(),
                exceptionType, exceptionMessage);
    }

    @Transactional
    public DeadLetter redrive(UUID id) {
        var deadLetter = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no dead letter with id " + id));
        if (deadLetter.getRedrivenAt() != null) {
            return deadLetter;
        }
        StockMovementEvent event = readEvent(deadLetter);
        kafka.send(props.kafka().topics().stockMovements(), event.sku(), event);
        deadLetter.markRedriven(clock.instant());
        log.info("redrove dead letter {} (event {})", id, event.eventId());
        return deadLetter;
    }

    public List<DeadLetter> pending(int limit) {
        return repository.findByRedrivenAtIsNullOrderByFailedAtAsc(Limit.of(limit));
    }

    public long pendingCount() {
        return repository.countByRedrivenAtIsNull();
    }

    private StockMovementEvent readEvent(DeadLetter deadLetter) {
        try {
            return json.readValue(deadLetter.getPayload(), StockMovementEvent.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(
                    "dead letter " + deadLetter.getId() + " payload is not a StockMovementEvent", e);
        }
    }
}
