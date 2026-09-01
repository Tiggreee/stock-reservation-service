package lat.vmdev.inventory.stockmovement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * A movement reported by the warehouse management system on
 * {@code wms.stock-movements.v1}. {@code eventId} is the de-duplication key.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockMovementEvent(
        String eventId,
        String sku,
        MovementType type,
        int quantity,
        Instant occurredAt) {}
