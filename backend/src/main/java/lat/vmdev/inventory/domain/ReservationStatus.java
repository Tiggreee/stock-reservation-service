package lat.vmdev.inventory.domain;

public enum ReservationStatus {

    /** Stock is held; the reservation can still be confirmed or released. */
    PENDING,

    /** The order was placed; the held stock has left the warehouse. */
    CONFIRMED,

    /** The hold was cancelled; stock returned to available. */
    RELEASED,

    /** The hold timed out before confirmation; stock returned to available. */
    EXPIRED
}
