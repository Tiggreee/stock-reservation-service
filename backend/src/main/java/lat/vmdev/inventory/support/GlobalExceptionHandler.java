package lat.vmdev.inventory.support;

import java.util.LinkedHashMap;
import java.util.Map;
import lat.vmdev.inventory.domain.InsufficientStockException;
import lat.vmdev.inventory.domain.StockRuleViolationException;
import lat.vmdev.inventory.observability.InventoryMetrics;
import lat.vmdev.inventory.reservation.ReservationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final InventoryMetrics metrics;

    public GlobalExceptionHandler(InventoryMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ApiError> insufficientStock(InsufficientStockException e) {
        metrics.reservationRejected("insufficient_stock");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "INSUFFICIENT_STOCK",
                e.getMessage(),
                Map.of("sku", e.getSku(), "requested", e.getRequested(), "available", e.getAvailable())));
    }

    @ExceptionHandler(ContendedStockException.class)
    ResponseEntity<ApiError> contended(ContendedStockException e) {
        metrics.reservationRejected("contended");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "1")
                .body(ApiError.of("STOCK_CONTENDED", e.getMessage()));
    }

    @ExceptionHandler(UnknownSkuException.class)
    ResponseEntity<ApiError> unknownSku(UnknownSkuException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("UNKNOWN_SKU", e.getMessage(), Map.of("sku", e.getSku())));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    ResponseEntity<ApiError> reservationNotFound(ReservationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESERVATION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(StockRuleViolationException.class)
    ResponseEntity<ApiError> ruleViolation(StockRuleViolationException e) {
        if (e.getMessage() != null && e.getMessage().contains("invariant violated")) {
            log.error("STOCK INVARIANT BREACH", e);
            metrics.oversellDetected();
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of("STOCK_RULE_VIOLATION", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_FAILED", "request failed validation", fields));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) throws Exception {
        // Let Spring handle its own web exceptions (404s, unsupported media type, ...).
        if (e instanceof org.springframework.web.ErrorResponse) {
            throw e;
        }
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "the request could not be completed"));
    }
}
