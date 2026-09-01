package lat.vmdev.inventory.domain;

/**
 * Thrown when an operation would break a stock rule that no amount of retrying
 * can fix — a negative quantity, releasing more than is reserved, an adjustment
 * that would drive stock below what is already committed.
 */
public class StockRuleViolationException extends RuntimeException {

    public StockRuleViolationException(String message) {
        super(message);
    }
}
