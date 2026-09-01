package lat.vmdev.inventory.stockmovement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lat.vmdev.inventory.config.InventoryProperties;
import lat.vmdev.inventory.stock.StockService;
import lat.vmdev.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class StockMovementConsumerIT extends AbstractIntegrationTest {

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    TestRestTemplate http;

    @Autowired
    StockService stock;

    @Autowired
    DeadLetterService deadLetters;

    @Autowired
    InventoryProperties props;

    private String topic() {
        return props.kafka().topics().stockMovements();
    }

    @Test
    void aReceiptRaisesOnHandStock() {
        String sku = "WMS-" + UUID.randomUUID().toString().substring(0, 8);

        kafka.send(topic(), sku, new StockMovementEvent(
                UUID.randomUUID().toString(), sku, MovementType.RECEIPT, 40, Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var view = http.getForObject("/api/v1/stock/" + sku, Map.class);
            assertThat(view).isNotNull();
            assertThat(view).containsEntry("onHand", 40).containsEntry("available", 40);
        });
    }

    @Test
    void aDuplicateEventIsAppliedExactlyOnce() {
        String sku = "WMS-" + UUID.randomUUID().toString().substring(0, 8);
        String eventId = UUID.randomUUID().toString();
        var event = new StockMovementEvent(eventId, sku, MovementType.RECEIPT, 15, Instant.now());

        kafka.send(topic(), sku, event);
        kafka.send(topic(), sku, event);
        kafka.send(topic(), sku, event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var view = http.getForObject("/api/v1/stock/" + sku, Map.class);
            assertThat(view).isNotNull().containsEntry("onHand", 15);
        });
        // give any duplicate a chance to be wrongly applied, then re-check
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        assertThat(http.getForObject("/api/v1/stock/" + sku, Map.class)).containsEntry("onHand", 15);
    }

    @Test
    void aPermanentFailureIsDeadLetteredNotRetriedForever() {
        String sku = "WMS-" + UUID.randomUUID().toString().substring(0, 8);
        stock.open(sku, 5);
        long before = deadLetters.pendingCount();

        // An adjustment that would drive stock negative is a rule violation: no retry helps.
        kafka.send(topic(), sku, new StockMovementEvent(
                UUID.randomUUID().toString(), sku, MovementType.ADJUSTMENT, -50, Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deadLetters.pendingCount()).isEqualTo(before + 1));

        var recorded = deadLetters.pending(200).stream()
                .filter(d -> d.getPayload().contains(sku))
                .findFirst()
                .orElseThrow();
        assertThat(recorded.getExceptionMsg()).contains("NonRetryableMovementException");
        assertThat(http.getForObject("/api/v1/stock/" + sku, Map.class)).containsEntry("onHand", 5);
    }
}
