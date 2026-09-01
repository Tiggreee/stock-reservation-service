package lat.vmdev.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StockLevelTest {

    @Test
    void reserveReducesAvailableStock() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);

        stock.reserve(4);

        assertThat(stock.available()).isEqualTo(6);
        assertThat(stock.getReserved()).isEqualTo(4);
        assertThat(stock.getOnHand()).isEqualTo(10);
    }

    @Test
    void reserveMoreThanAvailableThrowsAndLeavesStockUntouched() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);
        stock.reserve(8);

        assertThatThrownBy(() -> stock.reserve(3))
                .isInstanceOf(InsufficientStockException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(InsufficientStockException.class))
                .satisfies(ex -> {
                    assertThat(ex.getAvailable()).isEqualTo(2);
                    assertThat(ex.getRequested()).isEqualTo(3);
                });

        assertThat(stock.getReserved()).isEqualTo(8);
    }

    @Test
    void commitRemovesFromReservedAndOnHand() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);
        stock.reserve(6);

        stock.commit(6);

        assertThat(stock.getOnHand()).isEqualTo(4);
        assertThat(stock.getReserved()).isZero();
        assertThat(stock.available()).isEqualTo(4);
    }

    @Test
    void releaseReturnsStockToAvailable() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);
        stock.reserve(6);

        stock.release(2);

        assertThat(stock.getReserved()).isEqualTo(4);
        assertThat(stock.available()).isEqualTo(6);
    }

    @Test
    void releaseMoreThanReservedThrows() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);
        stock.reserve(3);

        assertThatThrownBy(() -> stock.release(5)).isInstanceOf(StockRuleViolationException.class);
    }

    @Test
    void adjustDownBelowReservedThrows() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);
        stock.reserve(8);

        assertThatThrownBy(() -> stock.adjust(-5)).isInstanceOf(StockRuleViolationException.class);
        assertThat(stock.getOnHand()).isEqualTo(10);
    }

    @Test
    void receiveIncreasesOnHand() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);

        stock.receive(15);

        assertThat(stock.getOnHand()).isEqualTo(25);
        assertThat(stock.available()).isEqualTo(25);
    }

    @Test
    void nonPositiveQuantitiesRejected() {
        var stock = new StockLevel("SKU-1", "MAIN", 10);

        assertThatThrownBy(() -> stock.reserve(0)).isInstanceOf(StockRuleViolationException.class);
        assertThatThrownBy(() -> stock.reserve(-1)).isInstanceOf(StockRuleViolationException.class);
    }
}
