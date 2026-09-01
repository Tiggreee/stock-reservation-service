package lat.vmdev.inventory.reservation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lat.vmdev.inventory.reservation.api.ConfirmReservationRequest;
import lat.vmdev.inventory.reservation.api.CreateReservationRequest;
import lat.vmdev.inventory.reservation.api.ReservationView;
import lat.vmdev.inventory.reservation.api.StockView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationView> create(
            @Valid @RequestBody CreateReservationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {

        String key = idempotencyHeader != null && !idempotencyHeader.isBlank()
                ? idempotencyHeader
                : request.idempotencyKey();

        var reservation = reservations.reserve(new ReserveCommand(request.sku(), request.quantity(), key));
        return ResponseEntity
                .created(URI.create("/api/v1/reservations/" + reservation.getId()))
                .body(ReservationView.of(reservation));
    }

    @GetMapping("/reservations/{id}")
    public ReservationView get(@PathVariable UUID id) {
        return ReservationView.of(reservations.get(id));
    }

    @GetMapping("/reservations")
    public List<ReservationView> recent(@RequestParam(defaultValue = "50") int limit) {
        return reservations.recent(Math.min(Math.max(limit, 1), 200)).stream()
                .map(ReservationView::of)
                .toList();
    }

    @PostMapping("/reservations/{id}/confirm")
    public ReservationView confirm(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ConfirmReservationRequest request) {
        String orderRef = request == null ? null : request.orderRef();
        return ReservationView.of(reservations.confirm(id, orderRef));
    }

    @DeleteMapping("/reservations/{id}")
    public ReservationView release(@PathVariable UUID id) {
        return ReservationView.of(reservations.release(id, "released by client"));
    }

    @GetMapping("/stock/{sku}")
    public StockView stock(@PathVariable String sku) {
        return StockView.of(reservations.stockFor(sku));
    }
}
