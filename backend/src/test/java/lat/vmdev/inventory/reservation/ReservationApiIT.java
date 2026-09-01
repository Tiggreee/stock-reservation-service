package lat.vmdev.inventory.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.Map;
import java.util.UUID;
import lat.vmdev.inventory.stock.StockService;
import lat.vmdev.inventory.support.AbstractIntegrationTest;
import lat.vmdev.inventory.support.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

class ReservationApiIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    StockService stock;

    private String sku;

    @BeforeEach
    void seedStock() {
        sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        stock.open(sku, 5);
    }

    @Test
    void reservesStockAndReducesAvailability() {
        var created = http.postForEntity("/api/v1/reservations",
                Map.of("sku", sku, "quantity", 2, "idempotencyKey", UUID.randomUUID().toString()),
                Map.class);

        assertThat(created.getStatusCode()).isEqualTo(CREATED);
        assertThat(created.getBody()).containsEntry("status", "PENDING").containsEntry("quantity", 2);

        var stockView = http.getForObject("/api/v1/stock/" + sku, Map.class);
        assertThat(stockView).containsEntry("available", 3).containsEntry("reserved", 2);
    }

    @Test
    void rejectsAReservationLargerThanAvailableStockWith409() {
        var response = http.postForEntity("/api/v1/reservations",
                Map.of("sku", sku, "quantity", 9, "idempotencyKey", UUID.randomUUID().toString()),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(response.getBody().details()).containsEntry("available", 5).containsEntry("requested", 9);
    }

    @Test
    void replayingAnIdempotencyKeyReturnsTheOriginalReservation() {
        String key = UUID.randomUUID().toString();
        var body = Map.of("sku", sku, "quantity", 1, "idempotencyKey", key);

        var first = http.postForEntity("/api/v1/reservations", body, Map.class);
        var second = http.postForEntity("/api/v1/reservations", body, Map.class);

        assertThat(first.getBody().get("id")).isEqualTo(second.getBody().get("id"));

        var stockView = http.getForObject("/api/v1/stock/" + sku, Map.class);
        assertThat(stockView).containsEntry("reserved", 1); // charged once, not twice
    }

    @Test
    void confirmingAReservationCommitsTheStock() {
        String key = UUID.randomUUID().toString();
        var created = http.postForEntity("/api/v1/reservations",
                Map.of("sku", sku, "quantity", 2, "idempotencyKey", key), Map.class);
        String id = (String) created.getBody().get("id");

        var confirmed = http.exchange("/api/v1/reservations/" + id + "/confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("orderRef", "ORD-1"), jsonHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(confirmed.getBody()).containsEntry("status", "CONFIRMED").containsEntry("orderRef", "ORD-1");

        var stockView = http.getForObject("/api/v1/stock/" + sku, Map.class);
        assertThat(stockView).containsEntry("onHand", 3).containsEntry("reserved", 0);
    }

    private static HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        return headers;
    }
}
