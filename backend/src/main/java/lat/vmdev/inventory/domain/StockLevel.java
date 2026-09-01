package lat.vmdev.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * The stock position for one SKU at one location, and the single place the
 * invariant is enforced in the domain:
 *
 * <pre>
 *   reserved &gt;= 0  AND  onHand &gt;= 0  AND  reserved &lt;= onHand
 * </pre>
 *
 * {@code available()} is the derived value {@code onHand - reserved} and can
 * never be negative. The {@link Version} column turns a lost update under
 * concurrency into an optimistic-lock failure rather than silent corruption.
 */
@Entity
@Table(
        name = "stock_level",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stock_level_sku_location",
                columnNames = {"sku", "location"}))
public class StockLevel {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false, updatable = false)
    private String location;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Version
    @Column(nullable = false)
    private long version;

    protected StockLevel() {
        // for JPA
    }

    public StockLevel(String sku, String location, int onHand) {
        if (onHand < 0) {
            throw new StockRuleViolationException("initial on-hand for %s cannot be negative".formatted(sku));
        }
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.location = location;
        this.onHand = onHand;
        this.reserved = 0;
    }

    public int available() {
        return onHand - reserved;
    }

    /** Hold stock for a pending reservation. */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > available()) {
            throw new InsufficientStockException(sku, quantity, available());
        }
        reserved += quantity;
        checkInvariant();
    }

    /** Return a previously held reservation to available stock. */
    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved) {
            throw new StockRuleViolationException(
                    "cannot release %d units of %s; only %d reserved".formatted(quantity, sku, reserved));
        }
        reserved -= quantity;
        checkInvariant();
    }

    /** Commit a reservation: the held stock leaves the warehouse. */
    public void commit(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved || quantity > onHand) {
            throw new StockRuleViolationException(
                    "cannot commit %d units of %s (reserved=%d, onHand=%d)"
                            .formatted(quantity, sku, reserved, onHand));
        }
        reserved -= quantity;
        onHand -= quantity;
        checkInvariant();
    }

    /** Goods received into the warehouse. */
    public void receive(int quantity) {
        requirePositive(quantity);
        onHand += quantity;
        checkInvariant();
    }

    /** Correction from a stock count; {@code delta} may be negative. */
    public void adjust(int delta) {
        int newOnHand = onHand + delta;
        if (newOnHand < 0) {
            throw new StockRuleViolationException(
                    "adjustment of %d would drive %s on-hand negative".formatted(delta, sku));
        }
        if (newOnHand < reserved) {
            throw new StockRuleViolationException(
                    "adjustment of %d would leave %s with fewer on-hand (%d) than reserved (%d)"
                            .formatted(delta, sku, newOnHand, reserved));
        }
        onHand = newOnHand;
        checkInvariant();
    }

    private void checkInvariant() {
        if (reserved < 0 || onHand < 0 || reserved > onHand) {
            throw new StockRuleViolationException(
                    "invariant violated for %s: onHand=%d reserved=%d".formatted(sku, onHand, reserved));
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new StockRuleViolationException("quantity must be positive, was " + quantity);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getLocation() {
        return location;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public long getVersion() {
        return version;
    }
}
