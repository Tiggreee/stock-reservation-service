package lat.vmdev.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of every change to a stock level. The ledger is the
 * reconciliation source of truth: summing {@code quantityDelta} for a SKU must
 * always match its current position.
 */
@Entity
@Table(
        name = "stock_ledger",
        indexes = {
            @Index(name = "ix_stock_ledger_sku", columnList = "sku"),
            @Index(name = "ix_stock_ledger_correlation", columnList = "correlation_id")
        })
public class StockLedgerEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private LedgerEntryType type;

    @Column(name = "quantity_delta", nullable = false, updatable = false)
    private int quantityDelta;

    @Column(name = "on_hand_after", nullable = false, updatable = false)
    private int onHandAfter;

    @Column(name = "reserved_after", nullable = false, updatable = false)
    private int reservedAfter;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockLedgerEntry() {
        // for JPA
    }

    public StockLedgerEntry(
            LedgerEntryType type,
            int quantityDelta,
            StockLevel resulting,
            String correlationId,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.sku = resulting.getSku();
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.onHandAfter = resulting.getOnHand();
        this.reservedAfter = resulting.getReserved();
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public int getOnHandAfter() {
        return onHandAfter;
    }

    public int getReservedAfter() {
        return reservedAfter;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
