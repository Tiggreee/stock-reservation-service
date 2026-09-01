package lat.vmdev.inventory.stockmovement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A movement record that exhausted its retries or hit a permanent error. */
@Entity
@Table(name = "dead_letter")
public class DeadLetter {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 128)
    private String topic;

    @Column(name = "partition_no", updatable = false)
    private Integer partitionNo;

    @Column(name = "kafka_offset", updatable = false)
    private Long kafkaOffset;

    @Column(name = "message_key", updatable = false, length = 256)
    private String messageKey;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "exception_type", updatable = false, length = 256)
    private String exceptionType;

    @Column(name = "exception_msg", updatable = false, columnDefinition = "text")
    private String exceptionMsg;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private Instant failedAt;

    @Column(name = "redriven_at")
    private Instant redrivenAt;

    protected DeadLetter() {
        // for JPA
    }

    public DeadLetter(
            String topic,
            Integer partitionNo,
            Long kafkaOffset,
            String messageKey,
            String payload,
            String exceptionType,
            String exceptionMsg,
            Instant failedAt) {
        this.id = UUID.randomUUID();
        this.topic = topic;
        this.partitionNo = partitionNo;
        this.kafkaOffset = kafkaOffset;
        this.messageKey = messageKey;
        this.payload = payload;
        this.exceptionType = exceptionType;
        this.exceptionMsg = exceptionMsg;
        this.failedAt = failedAt;
    }

    public void markRedriven(Instant when) {
        this.redrivenAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public Integer getPartitionNo() {
        return partitionNo;
    }

    public Long getKafkaOffset() {
        return kafkaOffset;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getExceptionMsg() {
        return exceptionMsg;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getRedrivenAt() {
        return redrivenAt;
    }
}
