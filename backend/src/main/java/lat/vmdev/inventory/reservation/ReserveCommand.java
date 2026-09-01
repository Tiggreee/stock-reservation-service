package lat.vmdev.inventory.reservation;

public record ReserveCommand(String sku, int quantity, String idempotencyKey) {}
