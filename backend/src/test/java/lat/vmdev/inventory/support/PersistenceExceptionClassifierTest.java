package lat.vmdev.inventory.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLTransientConnectionException;
import lat.vmdev.inventory.domain.StockRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class PersistenceExceptionClassifierTest {

    private final PersistenceExceptionClassifier classifier = new PersistenceExceptionClassifier();

    @Test
    void optimisticLockFailureIsContention() {
        assertThat(classifier.classify(new ObjectOptimisticLockingFailureException(Object.class, 1L)))
                .isEqualTo(FailureClass.CONTENTION);
    }

    @Test
    void deadlockAndLockTimeoutAreTransient() {
        assertThat(classifier.classify(new CannotAcquireLockException("deadlock detected")))
                .isEqualTo(FailureClass.TRANSIENT);
        assertThat(classifier.classify(new QueryTimeoutException("lock wait timeout")))
                .isEqualTo(FailureClass.TRANSIENT);
    }

    @Test
    void lostConnectionIsTransientEvenThoughSpringFilesItAsNonTransient() {
        assertThat(classifier.classify(new DataAccessResourceFailureException("connection reset")))
                .isEqualTo(FailureClass.TRANSIENT);
    }

    @Test
    void constraintViolationIsPermanent() {
        assertThat(classifier.classify(new DataIntegrityViolationException("ck_stock_level_invariant")))
                .isEqualTo(FailureClass.PERMANENT);
    }

    @Test
    void businessRuleRejectionIsPermanent() {
        assertThat(classifier.classify(new StockRuleViolationException("would go negative")))
                .isEqualTo(FailureClass.PERMANENT);
    }

    @Test
    void classificationLooksThroughWrappingCauses() {
        var wrapped = new RuntimeException("listener failed",
                new IllegalStateException("boom", new SQLTransientConnectionException("no connections")));
        assertThat(classifier.classify(wrapped)).isEqualTo(FailureClass.TRANSIENT);
    }

    @Test
    void unrecognisedFailureFailsClosedAsPermanent() {
        assertThat(classifier.classify(new RuntimeException("who knows")))
                .isEqualTo(FailureClass.PERMANENT);
    }
}
