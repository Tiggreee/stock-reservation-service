package lat.vmdev.inventory.support;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape the API returns. {@code code} is a stable machine
 * token the client switches on; {@code details} carries structured context.
 */
public record ApiError(String code, String message, Instant timestamp, Map<String, Object> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), Map.of());
    }

    public static ApiError of(String code, String message, Map<String, Object> details) {
        return new ApiError(code, message, Instant.now(), details);
    }
}
