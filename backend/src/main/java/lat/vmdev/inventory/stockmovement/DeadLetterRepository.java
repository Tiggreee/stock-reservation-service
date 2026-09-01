package lat.vmdev.inventory.stockmovement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {

    List<DeadLetter> findByRedrivenAtIsNullOrderByFailedAtAsc(Limit limit);

    long countByRedrivenAtIsNull();
}
