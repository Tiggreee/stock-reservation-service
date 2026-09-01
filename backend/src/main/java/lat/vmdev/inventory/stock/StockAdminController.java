package lat.vmdev.inventory.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lat.vmdev.inventory.reservation.api.StockView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator endpoints for seeding and correcting stock outside the reservation
 * flow. In production these would sit behind an admin scope.
 */
@RestController
@RequestMapping("/api/v1/stock")
public class StockAdminController {

    private final StockService stock;

    public StockAdminController(StockService stock) {
        this.stock = stock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockView open(@Valid @RequestBody OpenStockRequest request) {
        return StockView.of(stock.open(request.sku(), request.onHand()));
    }

    @PostMapping("/{sku}/receipts")
    public StockView receive(@PathVariable String sku, @Valid @RequestBody ReceiveStockRequest request) {
        return StockView.of(stock.receive(sku, request.quantity(), request.reference()));
    }

    @PostMapping("/{sku}/adjustments")
    public StockView adjust(@PathVariable String sku, @Valid @RequestBody AdjustStockRequest request) {
        return StockView.of(stock.adjust(sku, request.delta(), request.reference()));
    }

    public record OpenStockRequest(
            @NotBlank @Size(max = 64) String sku,
            @PositiveOrZero int onHand) {}

    public record ReceiveStockRequest(
            @Positive int quantity,
            @Size(max = 128) String reference) {}

    public record AdjustStockRequest(
            int delta,
            @Size(max = 128) String reference) {}
}
