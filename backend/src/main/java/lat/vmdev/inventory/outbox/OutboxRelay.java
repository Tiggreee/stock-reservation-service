package lat.vmdev.inventory.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import lat.vmdev.inventory.config.InventoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and publishes pending events to {@code inventory.events.v1},
 * keyed by aggregate id so a given aggregate's events stay ordered. A send
 * failure aborts the batch and leaves the rows unpublished for the next tick;
 * downstream consumers are expected to de-duplicate.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;
    private final Clock clock;
    private final InventoryProperties props;

    public OutboxRelay(
            OutboxRepository outbox,
            KafkaTemplate<String, Object> kafka,
            ObjectMapper json,
            Clock clock,
            InventoryProperties props) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
        this.clock = clock;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.poll-interval-ms}")
    @Transactional
    public void publishPending() throws Exception {
        var batch = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(props.outbox().batchSize()));
        if (batch.isEmpty()) {
            return;
        }
        String topic = props.kafka().topics().inventoryEvents();
        for (OutboxEvent event : batch) {
            JsonNode payload = json.readTree(event.getPayload());
            kafka.send(topic, event.getAggregateId(), payload).get(10, TimeUnit.SECONDS);
            event.markPublished(clock.instant());
        }
        log.debug("relayed {} outbox event(s)", batch.size());
    }
}
