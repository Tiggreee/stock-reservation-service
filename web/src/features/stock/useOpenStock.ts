import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, RequestError } from "../../api/client";
import type { StockLevel } from "../../api/types";
import { stockKey } from "../reservations/queries";

/** Seed a SKU from the console so there is something to reserve against. */
export function useOpenStock() {
  const queryClient = useQueryClient();

  return useMutation<StockLevel, RequestError, { sku: string; onHand: number }>({
    mutationFn: ({ sku, onHand }) => api.openStock(sku, onHand),
    onSuccess: (stock) => {
      queryClient.setQueryData(stockKey(stock.sku), stock);
    },
  });
}
