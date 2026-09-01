package lat.vmdev.inventory.support;

import java.sql.SQLNonTransientException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import lat.vmdev.inventory.domain.StockRuleViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/**
 * Splits a failure into {@link FailureClass#CONTENTION}, {@link FailureClass#TRANSIENT}
 * or {@link FailureClass#PERMANENT} by walking the cause chain. Anything it
 * cannot place is treated as {@code PERMANENT} — unknown failures fail closed
 * and land in the dead-letter queue rather than retrying forever.
 */
@Component
public class PersistenceExceptionClassifier {

    public FailureClass classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            FailureClass placed = classifyOne(t);
            if (placed != null) {
                return placed;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return FailureClass.PERMANENT;
    }

    private FailureClass classifyOne(Throwable t) {
        if (t instanceof OptimisticLockingFailureException) {
            return FailureClass.CONTENTION;
        }
        // A dropped connection is retryable even though Spring files it as "non-transient".
        if (t instanceof DataAccessResourceFailureException
                || t instanceof TransientDataAccessException
                || t instanceof RecoverableDataAccessException
                || t instanceof SQLTransientException
                || t instanceof SQLRecoverableException) {
            return FailureClass.TRANSIENT;
        }
        if (t instanceof DataIntegrityViolationException
                || t instanceof StockRuleViolationException
                || t instanceof NonTransientDataAccessException
                || t instanceof SQLNonTransientException) {
            return FailureClass.PERMANENT;
        }
        return null;
    }
}
