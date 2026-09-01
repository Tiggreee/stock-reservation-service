package lat.vmdev.inventory.stock;

import java.time.Clock;
import java.util.function.ToIntFunction;
import lat.vmdev.inventory.config.InventoryProperties;
import lat.vmdev.inventory.domain.LedgerEntryType;
import lat.vmdev.inventory.domain.StockLedgerEntry;
import lat.vmdev.inventory.domain.StockLevel;
import lat.vmdev.inventory.domain.StockRuleViolationException;
import lat.vmdev.inventory.events.StockLevelChanged;
import lat.vmdev.inventory.persistence.StockLedgerRepository;
import lat.vmdev.inventory.persistence.StockLevelRepository;
import lat.vmdev.inventory.reservation.ReservationService;
import lat.vmdev.inventory.support.OptimisticRetry;
import lat.vmdev.inventory.support.UnknownSkuException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stock-level changes that do not involve a reservation: opening a new SKU,
 * goods receipts, and stock-count adjustments. Shared by the admin API and the
 * warehouse-movement Kafka consumer.
 */
@Service
public class StockService {

    private static final String LOCATION = ReservationService.LOCATION;

    private final TransactionTemplate tx;
    private final StockLevelRepository stockLevels;
    private final StockLedgerRepository ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final InventoryProperties props;

    public StockService(
            TransactionTemplate tx,
            StockLevelRepository stockLevels,
            StockLedgerRepository ledger,
            ApplicationEventPublisher events,
            Clock clock,
            InventoryProperties props) {
        this.tx = tx;
        this.stockLevels = stockLevels;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
        this.props = props;
    }

    public StockLevel open(String sku, int initialOnHand) {
        return tx.execute(status -> {
            stockLevels.findBySkuAndLocation(sku, LOCATION).ifPresent(existing -> {
                throw new StockRuleViolationException("stock level for %s already exists".formatted(sku));
            });
            var stock = new StockLevel(sku, LOCATION, initialOnHand);
            stockLevels.save(stock);
            if (initialOnHand > 0) {
                var now = clock.instant();
                ledger.save(new StockLedgerEntry(LedgerEntryType.RECEIPT, initialOnHand, stock, "opening-balance", now));
                events.publishEvent(new StockLevelChanged(
                        sku, LedgerEntryType.RECEIPT, initialOnHand,
                        stock.getOnHand(), stock.getReserved(), "opening-balance", now));
            }
            return stock;
        });
    }

    public StockLevel receive(String sku, int quantity, String correlationId) {
        return mutate(sku, LedgerEntryType.RECEIPT, correlationId, stock -> {
            stock.receive(quantity);
            return quantity;
        });
    }

    public StockLevel adjust(String sku, int delta, String correlationId) {
        return mutate(sku, LedgerEntryType.ADJUST, correlationId, stock -> {
            stock.adjust(delta);
            return delta;
        });
    }

    private StockLevel mutate(
            String sku, LedgerEntryType type, String correlationId, ToIntFunction<StockLevel> operation) {
        return OptimisticRetry.execute(
                props.reservation().optimisticLockMaxRetries(),
                sku,
                () -> tx.execute(status -> {
                    var stock = stockLevels.findBySkuAndLocation(sku, LOCATION)
                            .orElseThrow(() -> new UnknownSkuException(sku));
                    var now = clock.instant();
                    int delta = operation.applyAsInt(stock);
                    stockLevels.save(stock);
                    ledger.save(new StockLedgerEntry(type, delta, stock, correlationId, now));
                    events.publishEvent(new StockLevelChanged(
                            sku, type, delta, stock.getOnHand(), stock.getReserved(), correlationId, now));
                    return stock;
                }));
    }
}
