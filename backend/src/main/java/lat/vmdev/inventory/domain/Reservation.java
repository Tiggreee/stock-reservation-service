package lat.vmdev.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer's hold on stock. Lifecycle: {@code PENDING} then exactly one of
 * {@code CONFIRMED}, {@code RELEASED} or {@code EXPIRED}. The unique
 * {@code idempotency_key} makes a retried create return the original row rather
 * than book a second hold.
 */
@Entity
@Table(
        name = "reservation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reservation_idempotency_key",
                columnNames = "idempotency_key"))
public class Reservation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "order_ref")
    private String orderRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Reservation() {
        // for JPA
    }

    public Reservation(String sku, int quantity, String idempotencyKey, Instant now, Instant expiresAt) {
        if (quantity <= 0) {
            throw new StockRuleViolationException("reservation quantity must be positive, was " + quantity);
        }
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.status = ReservationStatus.PENDING;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public void confirm(Instant now) {
        transitionFromPending(ReservationStatus.CONFIRMED, "confirm", now);
    }

    public void release(Instant now) {
        transitionFromPending(ReservationStatus.RELEASED, "release", now);
    }

    public void expire(Instant now) {
        transitionFromPending(ReservationStatus.EXPIRED, "expire", now);
    }

    private void transitionFromPending(ReservationStatus target, String action, Instant now) {
        if (status != ReservationStatus.PENDING) {
            throw new StockRuleViolationException(
                    "cannot %s reservation %s in status %s".formatted(action, id, status));
        }
        this.status = target;
        this.settledAt = now;
    }

    public boolean isPending() {
        return status == ReservationStatus.PENDING;
    }

    public boolean hasExpired(Instant now) {
        return status == ReservationStatus.PENDING && now.isAfter(expiresAt);
    }

    public void assignOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public long getVersion() {
        return version;
    }
}
