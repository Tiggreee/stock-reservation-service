package lat.vmdev.inventory.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lat.vmdev.inventory.domain.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

    Optional<StockLevel> findBySkuAndLocation(String sku, String location);

    List<StockLevel> findBySku(String sku);

    /**
     * Pessimistic-lock variant, kept for SKUs hot enough that optimistic retry
     * churns (see ADR-005). Not on the default path.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.sku = :sku and s.location = :location")
    Optional<StockLevel> findBySkuAndLocationForUpdate(@Param("sku") String sku, @Param("location") String location);
}
