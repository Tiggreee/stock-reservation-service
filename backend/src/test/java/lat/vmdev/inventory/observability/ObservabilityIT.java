package lat.vmdev.inventory.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import lat.vmdev.inventory.stock.StockService;
import lat.vmdev.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;

@AutoConfigureObservability
class ObservabilityIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    StockService stock;

    @Test
    void healthEndpointReportsUp() {
        var health = http.getForObject("/actuator/health", Map.class);
        assertThat(health).containsEntry("status", "UP");
    }

    @Test
    void prometheusExposesInventoryMetricsAndOversellStaysZero() {
        String sku = "MET-" + UUID.randomUUID().toString().substring(0, 8);
        stock.open(sku, 5);
        http.postForEntity("/api/v1/reservations",
                Map.of("sku", sku, "quantity", 1, "idempotencyKey", UUID.randomUUID().toString()),
                Map.class);
        http.postForEntity("/api/v1/reservations",
                Map.of("sku", sku, "quantity", 50, "idempotencyKey", UUID.randomUUID().toString()),
                Map.class);

        String metrics = http.getForObject("/actuator/prometheus", String.class);

        assertThat(metrics).containsPattern("inventory_reservations_total\\{[^}]*outcome=\"created\"[^}]*}");
        assertThat(metrics).containsPattern("inventory_reservations_total\\{[^}]*outcome=\"rejected\"[^}]*}");
        assertThat(metrics).containsPattern("inventory_oversell_total(\\{[^}]*})? 0\\.0");
    }
}
