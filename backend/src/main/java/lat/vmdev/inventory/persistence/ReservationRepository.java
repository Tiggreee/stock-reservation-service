package lat.vmdev.inventory.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lat.vmdev.inventory.domain.Reservation;
import lat.vmdev.inventory.domain.ReservationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    List<Reservation> findByStatusAndExpiresAtBeforeOrderByExpiresAt(
            ReservationStatus status, Instant cutoff, Limit limit);

    List<Reservation> findByOrderByCreatedAtDesc(Limit limit);
}
