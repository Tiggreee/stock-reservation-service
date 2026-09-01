package lat.vmdev.inventory.reservation;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(UUID id) {
        super("no reservation with id " + id);
    }
}
