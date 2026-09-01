package lat.vmdev.inventory.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lat.vmdev.inventory.outbox.OutboxRepository;
import lat.vmdev.inventory.stockmovement.DeadLetterRepository;
import org.springframework.stereotype.Component;

/**
 * The business-level meters worth alerting on. {@code inventory.oversell.total}
 * must stay at zero; any increment means the invariant was breached and should
 * page.
 */
@Component
public class InventoryMetrics {

    private final MeterRegistry registry;
    private final Counter oversell;

    public InventoryMetrics(
            MeterRegistry registry, OutboxRepository outbox, DeadLetterRepository deadLetters) {
        this.registry = registry;
        this.oversell = Counter.builder("inventory.oversell.total")
                .description("stock-invariant breaches; must stay zero")
                .register(registry);

        // Pre-register the success counter so dashboards have a series from the start.
        reservationsCounter("created", "none");

        Gauge.builder("inventory.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull)
                .description("outbox events not yet published")
                .register(registry);
        Gauge.builder("inventory.deadletters.pending", deadLetters, DeadLetterRepository::countByRedrivenAtIsNull)
                .description("dead letters awaiting redrive")
                .register(registry);
    }

    public void reservationCreated() {
        reservationsCounter("created", "none").increment();
    }

    public void reservationRejected(String reason) {
        reservationsCounter("rejected", reason).increment();
    }

    public void oversellDetected() {
        oversell.increment();
    }

    public void movementDeadLettered() {
        registry.counter("inventory.stock_movement.dlt").increment();
    }

    private Counter reservationsCounter(String outcome, String reason) {
        return Counter.builder("inventory.reservations")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .description("reservation requests by outcome")
                .register(registry);
    }
}
