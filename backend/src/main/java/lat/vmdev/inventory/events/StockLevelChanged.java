package lat.vmdev.inventory.events;

import java.time.Instant;
import lat.vmdev.inventory.domain.LedgerEntryType;

public record StockLevelChanged(
        String sku,
        LedgerEntryType reason,
        int quantityDelta,
        int onHand,
        int reserved,
        String correlationId,
        Instant occurredAt)
        implements DomainEvent {

    @Override
    public String aggregateId() {
        return sku;
    }
}
