package lat.vmdev.inventory.reservation.api;

import jakarta.validation.constraints.Size;

public record ConfirmReservationRequest(@Size(max = 128) String orderRef) {}
