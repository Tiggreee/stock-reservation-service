package lat.vmdev.inventory.stockmovement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lat.vmdev.inventory.config.InventoryProperties;
import lat.vmdev.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * A transient failure is retried on delayed retry topics — not by blocking the
 * consumer — and the movement is applied once the failure clears.
 */
class StockMovementRetryIT extends AbstractIntegrationTest {

    @MockitoSpyBean
    StockMovementHandler handler;

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    TestRestTemplate http;

    @Autowired
    InventoryProperties props;

    @Test
    void aTransientFailureIsRetriedUntilItClears() {
        String sku = "RETRY-" + UUID.randomUUID().toString().substring(0, 8);

        // Fail the first two deliveries with a retryable error, then let it through.
        doThrow(new QueryTimeoutException("lock wait timeout"))
                .doThrow(new QueryTimeoutException("lock wait timeout"))
                .doCallRealMethod()
                .when(handler).handle(any(StockMovementEvent.class));

        kafka.send(props.kafka().topics().stockMovements(), sku, new StockMovementEvent(
                UUID.randomUUID().toString(), sku, MovementType.RECEIPT, 12, Instant.now()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var view = http.getForObject("/api/v1/stock/" + sku, Map.class);
            assertThat(view).isNotNull().containsEntry("onHand", 12);
        });
    }
}
