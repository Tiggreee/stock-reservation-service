package lat.vmdev.inventory.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lat.vmdev.inventory.stock.StockService;
import lat.vmdev.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class OutboxIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    StockService stock;

    @Autowired
    OutboxRepository outbox;

    @Test
    void aReservationStagesAndRelaysDomainEvents() {
        String sku = "OBX-" + UUID.randomUUID().toString().substring(0, 8);
        stock.open(sku, 10);

        http.postForEntity("/api/v1/reservations",
                Map.of("sku", sku, "quantity", 3, "idempotencyKey", UUID.randomUUID().toString()),
                Map.class);

        // ReservationCreated + StockLevelChanged are staged in the same transaction...
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var staged = outbox.findAll().stream()
                    .filter(e -> e.getPayload().contains(sku))
                    .toList();
            assertThat(staged).extracting(OutboxEvent::getEventType)
                    .contains("ReservationCreated", "StockLevelChanged");
            // ...and the relay publishes them and stamps published_at.
            assertThat(staged).allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull());
        });
    }

    @Test
    void nothingIsRelayedIfTheTransactionRollsBack() {
        String sku = "OBX-" + UUID.randomUUID().toString().substring(0, 8);
        stock.open(sku, 1);
        long before = outbox.count();

        // Over-reserve: the transaction fails, so no outbox row may be written.
        http.postForEntity("/api/v1/reservations",
                Map.of("sku", sku, "quantity", 99, "idempotencyKey", UUID.randomUUID().toString()),
                Map.class);

        assertThat(outbox.count()).isEqualTo(before);
    }
}
