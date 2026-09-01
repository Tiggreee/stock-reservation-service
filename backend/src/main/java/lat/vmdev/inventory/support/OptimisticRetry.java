package lat.vmdev.inventory.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Runs a unit of work that commits under optimistic locking, retrying in place
 * when another writer wins the version check. Each attempt must open its own
 * transaction — a failed commit marks the current one rollback-only.
 */
public final class OptimisticRetry {

    private static final Logger log = LoggerFactory.getLogger(OptimisticRetry.class);

    private OptimisticRetry() {}

    public static <T> T execute(int maxRetries, String context, Supplier<T> attempt) {
        for (int tries = 0; ; tries++) {
            try {
                return attempt.get();
            } catch (OptimisticLockingFailureException contention) {
                if (tries >= maxRetries) {
                    log.warn("optimistic-lock contention exhausted for {} after {} attempts", context, tries + 1);
                    throw new ContendedStockException(context, tries + 1);
                }
                backOff(tries);
            }
        }
    }

    private static void backOff(int attempt) {
        long micros = ThreadLocalRandom.current().nextLong(200, 1_500) * (attempt + 1L);
        try {
            Thread.sleep(micros / 1_000, (int) (micros % 1_000) * 1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while retrying a contended write", e);
        }
    }
}
