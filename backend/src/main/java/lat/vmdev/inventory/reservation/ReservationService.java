package lat.vmdev.inventory.reservation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lat.vmdev.inventory.config.InventoryProperties;
import lat.vmdev.inventory.domain.LedgerEntryType;
import lat.vmdev.inventory.domain.Reservation;
import lat.vmdev.inventory.domain.StockLedgerEntry;
import lat.vmdev.inventory.domain.StockLevel;
import lat.vmdev.inventory.events.ReservationConfirmed;
import lat.vmdev.inventory.events.ReservationCreated;
import lat.vmdev.inventory.events.ReservationReleased;
import lat.vmdev.inventory.events.StockLevelChanged;
import lat.vmdev.inventory.observability.InventoryMetrics;
import lat.vmdev.inventory.persistence.ReservationRepository;
import lat.vmdev.inventory.persistence.StockLedgerRepository;
import lat.vmdev.inventory.persistence.StockLevelRepository;
import lat.vmdev.inventory.support.OptimisticRetry;
import lat.vmdev.inventory.support.UnknownSkuException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The synchronous reservation use case. Each write runs in its own transaction;
 * a lost optimistic-lock race is retried a bounded number of times before the
 * caller is told the SKU is contended.
 */
@Service
public class ReservationService {

    /** Single-warehouse for now; multi-location is out of scope. */
    public static final String LOCATION = "MAIN";

    private final TransactionTemplate tx;
    private final StockLevelRepository stockLevels;
    private final ReservationRepository reservations;
    private final StockLedgerRepository ledger;
    private final ApplicationEventPublisher events;
    private final InventoryMetrics metrics;
    private final Clock clock;
    private final InventoryProperties props;

    public ReservationService(
            TransactionTemplate tx,
            StockLevelRepository stockLevels,
            ReservationRepository reservations,
            StockLedgerRepository ledger,
            ApplicationEventPublisher events,
            InventoryMetrics metrics,
            Clock clock,
            InventoryProperties props) {
        this.tx = tx;
        this.stockLevels = stockLevels;
        this.reservations = reservations;
        this.ledger = ledger;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.props = props;
    }

    public Reservation reserve(ReserveCommand command) {
        var alreadyDone = reservations.findByIdempotencyKey(command.idempotencyKey());
        if (alreadyDone.isPresent()) {
            return alreadyDone.get();
        }
        try {
            return OptimisticRetry.execute(
                    props.reservation().optimisticLockMaxRetries(),
                    command.sku(),
                    () -> tx.execute(status -> doReserve(command)));
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same idempotency key won the unique constraint.
            return reservations.findByIdempotencyKey(command.idempotencyKey()).orElseThrow(() -> race);
        }
    }

    private Reservation doReserve(ReserveCommand command) {
        var replay = reservations.findByIdempotencyKey(command.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get();
        }
        var stock = requireStock(command.sku());
        var now = clock.instant();

        stock.reserve(command.quantity());
        var reservation = new Reservation(
                command.sku(),
                command.quantity(),
                command.idempotencyKey(),
                now,
                now.plus(props.reservation().ttl()));

        recordMovement(stock, LedgerEntryType.RESERVE, -command.quantity(), reservation.getId().toString(), now);
        reservations.save(reservation);
        events.publishEvent(new ReservationCreated(reservation.getId(), stock.getSku(), command.quantity(), now));
        metrics.reservationCreated();
        return reservation;
    }

    public Reservation confirm(UUID reservationId, String orderRef) {
        return OptimisticRetry.execute(
                props.reservation().optimisticLockMaxRetries(),
                "reservation " + reservationId,
                () -> tx.execute(status -> {
                    var reservation = requireReservation(reservationId);
                    if (!reservation.isPending()) {
                        return reservation;
                    }
                    var stock = requireStock(reservation.getSku());
                    var now = clock.instant();

                    reservation.confirm(now);
                    if (orderRef != null && !orderRef.isBlank()) {
                        reservation.assignOrderRef(orderRef);
                    }
                    stock.commit(reservation.getQuantity());
                    recordMovement(stock, LedgerEntryType.COMMIT, -reservation.getQuantity(),
                            reservation.getId().toString(), now);
                    events.publishEvent(new ReservationConfirmed(reservation.getId(), stock.getSku(),
                            reservation.getQuantity(), reservation.getOrderRef(), now));
                    return reservation;
                }));
    }

    public Reservation release(UUID reservationId, String reason) {
        return OptimisticRetry.execute(
                props.reservation().optimisticLockMaxRetries(),
                "reservation " + reservationId,
                () -> tx.execute(status -> {
                    var reservation = requireReservation(reservationId);
                    if (!reservation.isPending()) {
                        return reservation;
                    }
                    var stock = requireStock(reservation.getSku());
                    var now = clock.instant();

                    reservation.release(now);
                    stock.release(reservation.getQuantity());
                    recordMovement(stock, LedgerEntryType.RELEASE, reservation.getQuantity(),
                            reservation.getId().toString(), now);
                    events.publishEvent(new ReservationReleased(reservation.getId(), stock.getSku(),
                            reservation.getQuantity(), reason == null ? "released" : reason, now));
                    return reservation;
                }));
    }

    public Reservation get(UUID reservationId) {
        return reservations.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    public List<Reservation> recent(int limit) {
        return reservations.findByOrderByCreatedAtDesc(Limit.of(limit));
    }

    public StockLevel stockFor(String sku) {
        return requireStock(sku);
    }

    private void recordMovement(
            StockLevel stock, LedgerEntryType type, int delta, String correlationId, Instant now) {
        stockLevels.save(stock);
        ledger.save(new StockLedgerEntry(type, delta, stock, correlationId, now));
        events.publishEvent(new StockLevelChanged(
                stock.getSku(), type, delta, stock.getOnHand(), stock.getReserved(), correlationId, now));
    }

    private StockLevel requireStock(String sku) {
        return stockLevels.findBySkuAndLocation(sku, LOCATION)
                .orElseThrow(() -> new UnknownSkuException(sku));
    }

    private Reservation requireReservation(UUID id) {
        return reservations.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
    }
}
