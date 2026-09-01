package lat.vmdev.inventory.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lat.vmdev.inventory.domain.LedgerEntryType;
import lat.vmdev.inventory.persistence.StockLedgerRepository;
import lat.vmdev.inventory.stock.StockService;
import lat.vmdev.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

/**
 * The headline test: many callers race for the last few units of one SKU.
 * Correct behaviour is that the number of confirmed holds never exceeds the
 * stock that existed, the ledger agrees, and nobody oversells.
 */
class ReservationConcurrencyIT extends AbstractIntegrationTest {

    private static final int UNITS = 10;
    private static final int CALLERS = 200;

    @Autowired
    TestRestTemplate http;

    @Autowired
    StockService stock;

    @Autowired
    StockLedgerRepository ledger;

    @Test
    void concurrentReservationsNeverOversellTheLastUnits() throws Exception {
        String sku = "RACE-" + UUID.randomUUID().toString().substring(0, 8);
        stock.open(sku, UNITS);

        var reserved = new AtomicInteger();
        var soldOut = new AtomicInteger();
        var errors = new ConcurrentLinkedQueue<String>();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(CALLERS);

        try (var pool = Executors.newFixedThreadPool(32)) {
            for (int i = 0; i < CALLERS; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        attemptReservation(sku, reserved, soldOut, errors);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(errors).isEmpty();
        assertThat(reserved.get()).isEqualTo(UNITS);
        assertThat(soldOut.get()).isEqualTo(CALLERS - UNITS);

        var stockView = http.getForObject("/api/v1/stock/" + sku, Map.class);
        assertThat(stockView)
                .containsEntry("onHand", UNITS)
                .containsEntry("reserved", UNITS)
                .containsEntry("available", 0);

        long reserveEntries = ledger.findBySkuOrderByCreatedAtAsc(sku).stream()
                .filter(e -> e.getType() == LedgerEntryType.RESERVE)
                .count();
        assertThat(reserveEntries).isEqualTo(UNITS);
    }

    /** Mirrors a real client: retry a contended 409, accept a sold-out 409. */
    private void attemptReservation(
            String sku, AtomicInteger reserved, AtomicInteger soldOut, ConcurrentLinkedQueue<String> errors) {
        for (int attempt = 0; attempt < 15; attempt++) {
            var response = http.postForEntity("/api/v1/reservations",
                    Map.of("sku", sku, "quantity", 1, "idempotencyKey", UUID.randomUUID().toString()),
                    Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                reserved.incrementAndGet();
                return;
            }
            if (response.getStatusCode() == HttpStatus.CONFLICT) {
                Object code = response.getBody() == null ? null : response.getBody().get("code");
                if ("INSUFFICIENT_STOCK".equals(code)) {
                    soldOut.incrementAndGet();
                    return;
                }
                if ("STOCK_CONTENDED".equals(code)) {
                    continue; // lost the race, try again
                }
            }
            errors.add("unexpected " + response.getStatusCode() + " " + response.getBody());
            return;
        }
        errors.add("gave up after 15 contended attempts");
    }
}
