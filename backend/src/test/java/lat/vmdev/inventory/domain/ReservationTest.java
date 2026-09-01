package lat.vmdev.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    private Reservation pending() {
        return new Reservation("SKU-1", 3, "key-1", NOW, NOW.plus(15, ChronoUnit.MINUTES));
    }

    @Test
    void newReservationIsPending() {
        assertThat(pending().getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(pending().isPending()).isTrue();
    }

    @Test
    void confirmMovesToConfirmedAndStamps() {
        var reservation = pending();

        reservation.confirm(NOW.plusSeconds(30));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getSettledAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void cannotConfirmTwice() {
        var reservation = pending();
        reservation.confirm(NOW);

        assertThatThrownBy(() -> reservation.confirm(NOW)).isInstanceOf(StockRuleViolationException.class);
    }

    @Test
    void cannotReleaseAConfirmedReservation() {
        var reservation = pending();
        reservation.confirm(NOW);

        assertThatThrownBy(() -> reservation.release(NOW)).isInstanceOf(StockRuleViolationException.class);
    }

    @Test
    void hasExpiredOnlyWhenPendingAndPastExpiry() {
        var reservation = pending();

        assertThat(reservation.hasExpired(NOW.plus(10, ChronoUnit.MINUTES))).isFalse();
        assertThat(reservation.hasExpired(NOW.plus(20, ChronoUnit.MINUTES))).isTrue();

        reservation.confirm(NOW);
        assertThat(reservation.hasExpired(NOW.plus(20, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> new Reservation("SKU-1", 0, "k", NOW, NOW.plusSeconds(60)))
                .isInstanceOf(StockRuleViolationException.class);
    }
}
