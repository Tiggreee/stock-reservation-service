package lat.vmdev.inventory.persistence;

import java.util.List;
import java.util.UUID;
import lat.vmdev.inventory.domain.StockLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerRepository extends JpaRepository<StockLedgerEntry, UUID> {

    List<StockLedgerEntry> findBySkuOrderByCreatedAtAsc(String sku);
}
