import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, RequestError } from "../../api/client";
import type { Reservation, StockLevel } from "../../api/types";
import { useToast } from "../../components/toast-context";
import { reservationsKey, stockKey } from "./queries";

export interface ReserveInput {
  sku: string;
  quantity: number;
  /** Generated once per user intent and reused across retries, so a retry after
   *  a 500 or a timeout can never create a second reservation. */
  idempotencyKey: string;
}

/** What onMutate stashes so onError can restore the exact prior state. */
interface Snapshot {
  previousStock: StockLevel | undefined;
  previousReservations: Reservation[] | undefined;
  optimisticId: string;
}

function optimisticReservation(input: ReserveInput, id: string): Reservation {
  const now = new Date().toISOString();
  return {
    id,
    sku: input.sku,
    quantity: input.quantity,
    status: "PENDING",
    idempotencyKey: input.idempotencyKey,
    orderRef: null,
    createdAt: now,
    expiresAt: now,
    settledAt: null,
  };
}

export function useReserveStock(sku: string) {
  const queryClient = useQueryClient();
  const toast = useToast();

  return useMutation<Reservation, RequestError, ReserveInput, Snapshot>({
    mutationKey: ["reserve", sku],
    // Same-SKU reservations run one at a time so the snapshot chain stays coherent.
    scope: { id: `reserve:${sku}` },

    mutationFn: (input) => api.createReservation(input),

    // 1 — cancel in-flight reads, snapshot both caches, write the optimistic state.
    onMutate: async (input) => {
      await Promise.all([
        queryClient.cancelQueries({ queryKey: stockKey(input.sku) }),
        queryClient.cancelQueries({ queryKey: reservationsKey }),
      ]);

      const snapshot: Snapshot = {
        previousStock: queryClient.getQueryData<StockLevel>(stockKey(input.sku)),
        previousReservations: queryClient.getQueryData<Reservation[]>(reservationsKey),
        optimisticId: `optimistic-${input.idempotencyKey}`,
      };

      queryClient.setQueryData<StockLevel>(stockKey(input.sku), (current) =>
        current ? { ...current, available: current.available - input.quantity } : current,
      );
      queryClient.setQueryData<Reservation[]>(reservationsKey, (current = []) => [
        optimisticReservation(input, snapshot.optimisticId),
        ...current,
      ]);

      return snapshot;
    },

    // 2 — restore the snapshot, then react to the kind of failure.
    onError: (error, input, snapshot) => {
      if (snapshot?.previousStock !== undefined) {
        queryClient.setQueryData(stockKey(input.sku), snapshot.previousStock);
      }
      if (snapshot?.previousReservations !== undefined) {
        queryClient.setQueryData(reservationsKey, snapshot.previousReservations);
      }

      switch (error.kind) {
        case "conflict":
          toast.push(
            error.body?.code === "STOCK_CONTENDED"
              ? "That SKU is busy right now — try again."
              : "Just sold out — someone reserved the last unit.",
          );
          break;
        case "validation":
          toast.push(error.body?.message ?? "That reservation isn't valid.");
          break;
        case "timeout":
          toast.push("Status unknown — refreshing to check.");
          break;
        default:
          toast.push("Couldn't reserve — stock restored. Retrying…");
      }
    },

    // 3 — always reconcile with server truth (covers the timeout "unknown" case too).
    onSettled: (_data, _error, input) => {
      void queryClient.invalidateQueries({ queryKey: stockKey(input.sku) });
      void queryClient.invalidateQueries({ queryKey: reservationsKey });
    },

    // Retry only genuinely transient failures; a 409 or 422 will never succeed on retry.
    retry: (failureCount, error) => error.kind === "transient" && failureCount < 3,
    retryDelay: (attempt) => Math.min(500 * 2 ** attempt, 4000),
  });
}
