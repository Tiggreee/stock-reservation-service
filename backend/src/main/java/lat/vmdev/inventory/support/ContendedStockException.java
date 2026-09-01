package lat.vmdev.inventory.support;

/**
 * A write kept losing the optimistic-lock race after the configured number of
 * in-transaction retries. The caller should retry the request.
 */
public class ContendedStockException extends RuntimeException {

    public ContendedStockException(String context, int attempts) {
        super("could not update %s: lost the optimistic-lock race %d times".formatted(context, attempts));
    }
}
