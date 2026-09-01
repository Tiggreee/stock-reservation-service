package lat.vmdev.inventory.support;

/**
 * How a failure should be handled, decided from the exception rather than from
 * where it was thrown.
 */
public enum FailureClass {

    /** Optimistic-lock loss: another writer won the race. Retry in place. */
    CONTENTION,

    /** Deadlock, lock timeout, lost connection: retry later with backoff. */
    TRANSIENT,

    /** Constraint violation, bad payload, rule rejection: no retry will help. */
    PERMANENT
}
