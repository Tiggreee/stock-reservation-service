package lat.vmdev.inventory.stockmovement;

/**
 * A movement that cannot succeed no matter how many times it is retried — a
 * malformed payload, a constraint violation, or a rule rejection. Excluded from
 * {@code @RetryableTopic} so it goes straight to the dead-letter topic.
 */
public class NonRetryableMovementException extends RuntimeException {

    public NonRetryableMovementException(String message, Throwable cause) {
        super(message, cause);
    }
}
