package lat.vmdev.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed binding for the {@code inventory.*} configuration tree.
 */
@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(Reservation reservation, Outbox outbox, Kafka kafka) {

    public record Reservation(Duration ttl, int optimisticLockMaxRetries) {}

    public record Outbox(Duration pollInterval, int batchSize) {}

    public record Kafka(Topics topics, Retry retry) {

        public record Topics(String stockMovements, String inventoryEvents) {}

        public record Retry(int attempts, Duration initialInterval, double multiplier, Duration maxInterval) {}
    }
}
