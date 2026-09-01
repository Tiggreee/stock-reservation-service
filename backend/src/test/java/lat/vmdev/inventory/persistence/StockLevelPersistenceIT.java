package lat.vmdev.inventory.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lat.vmdev.inventory.domain.StockLevel;
import lat.vmdev.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class StockLevelPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    StockLevelRepository stockLevels;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @Transactional
    void persistsAndReloadsAStockLevel() {
        var saved = stockLevels.save(new StockLevel("SKU-PERSIST", "MAIN", 25));
        stockLevels.flush();

        var reloaded = stockLevels.findBySkuAndLocation("SKU-PERSIST", "MAIN").orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(reloaded.getOnHand()).isEqualTo(25);
        assertThat(reloaded.available()).isEqualTo(25);
    }

    @Test
    void databaseRejectsRowsThatBreakTheInvariant() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into stock_level (id, sku, location, on_hand, reserved, version) "
                        + "values (?, 'SKU-BAD', 'MAIN', 5, 9, 0)",
                java.util.UUID.randomUUID()))
                .hasMessageContaining("ck_stock_level_invariant");
    }
}
