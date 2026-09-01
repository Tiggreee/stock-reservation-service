package lat.vmdev.inventory.domain;

/**
 * Thrown when a reservation asks for more units than are currently available.
 * This is an expected outcome under contention, not a bug — the caller sees it
 * as HTTP 409.
 */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super("insufficient stock for %s: requested %d, available %d".formatted(sku, requested, available));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
