package lat.vmdev.inventory.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A domain event staged for publication. Written in the same transaction as the
 * state change it describes, so it is published if and only if that change
 * committed.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 128)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // for JPA
    }

    public OutboxEvent(
            String aggregateType, String aggregateId, String eventType, String payload, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
