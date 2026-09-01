package lat.vmdev.inventory.stockmovement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operator view of the dead-letter queue, and one-call redrive. */
@RestController
@RequestMapping("/api/v1/dead-letters")
public class DeadLetterController {

    private final DeadLetterService deadLetters;

    public DeadLetterController(DeadLetterService deadLetters) {
        this.deadLetters = deadLetters;
    }

    @GetMapping
    public List<DeadLetterView> pending(@RequestParam(defaultValue = "50") int limit) {
        return deadLetters.pending(Math.min(Math.max(limit, 1), 200)).stream()
                .map(DeadLetterView::of)
                .toList();
    }

    @PostMapping("/{id}/redrive")
    public DeadLetterView redrive(@PathVariable UUID id) {
        return DeadLetterView.of(deadLetters.redrive(id));
    }

    public record DeadLetterView(
            UUID id,
            String topic,
            Integer partition,
            Long offset,
            String key,
            String payload,
            String exceptionType,
            String exceptionMessage,
            Instant failedAt,
            Instant redrivenAt) {

        static DeadLetterView of(DeadLetter d) {
            return new DeadLetterView(
                    d.getId(),
                    d.getTopic(),
                    d.getPartitionNo(),
                    d.getKafkaOffset(),
                    d.getMessageKey(),
                    d.getPayload(),
                    d.getExceptionType(),
                    d.getExceptionMsg(),
                    d.getFailedAt(),
                    d.getRedrivenAt());
        }
    }
}
