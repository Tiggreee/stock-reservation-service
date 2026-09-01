package lat.vmdev.inventory.stockmovement;

public enum MovementType {

    /** Goods arrived at the warehouse; quantity is positive. */
    RECEIPT,

    /** Stock-count correction; quantity is a signed delta. */
    ADJUSTMENT
}
