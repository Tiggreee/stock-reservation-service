import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, RequestError } from "../../api/client";
import type { Reservation, StockLevel } from "../../api/types";
import { useToast } from "../../components/toast-context";
import { reservationsKey, stockKey } from "./queries";

interface Snapshot {
  previousStock: StockLevel | undefined;
  previousReservations: Reservation[] | undefined;
}

/** Optimistically mark a reservation released and hand its units back to available. */
export function useReleaseReservation() {
  const queryClient = useQueryClient();
  const toast = useToast();

  return useMutation<Reservation, RequestError, Reservation, Snapshot>({
    mutationFn: (reservation) => api.releaseReservation(reservation.id),

    onMutate: async (reservation) => {
      await Promise.all([
        queryClient.cancelQueries({ queryKey: stockKey(reservation.sku) }),
        queryClient.cancelQueries({ queryKey: reservationsKey }),
      ]);

      const snapshot: Snapshot = {
        previousStock: queryClient.getQueryData<StockLevel>(stockKey(reservation.sku)),
        previousReservations: queryClient.getQueryData<Reservation[]>(reservationsKey),
      };

      queryClient.setQueryData<StockLevel>(stockKey(reservation.sku), (current) =>
        current ? { ...current, available: current.available + reservation.quantity } : current,
      );
      queryClient.setQueryData<Reservation[]>(reservationsKey, (current = []) =>
        current.map((r) => (r.id === reservation.id ? { ...r, status: "RELEASED" } : r)),
      );

      return snapshot;
    },

    onError: (_error, reservation, snapshot) => {
      if (snapshot?.previousStock !== undefined) {
        queryClient.setQueryData(stockKey(reservation.sku), snapshot.previousStock);
      }
      if (snapshot?.previousReservations !== undefined) {
        queryClient.setQueryData(reservationsKey, snapshot.previousReservations);
      }
      toast.push("Couldn't release that hold — restored.");
    },

    onSettled: (_data, _error, reservation) => {
      void queryClient.invalidateQueries({ queryKey: stockKey(reservation.sku) });
      void queryClient.invalidateQueries({ queryKey: reservationsKey });
    },

    retry: (failureCount, error) => error.kind === "transient" && failureCount < 3,
  });
}
