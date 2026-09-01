package lat.vmdev.inventory.reservation.api;

import lat.vmdev.inventory.domain.StockLevel;

public record StockView(
        String sku,
        String location,
        int onHand,
        int reserved,
        int available,
        long version) {

    public static StockView of(StockLevel s) {
        return new StockView(
                s.getSku(),
                s.getLocation(),
                s.getOnHand(),
                s.getReserved(),
                s.available(),
                s.getVersion());
    }
}
