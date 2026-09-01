import { queryOptions } from "@tanstack/react-query";
import { api } from "../../api/client";

export const stockKey = (sku: string) => ["stock", sku] as const;
export const reservationsKey = ["reservations"] as const;

export const stockQuery = (sku: string) =>
  queryOptions({
    queryKey: stockKey(sku),
    queryFn: () => api.getStock(sku),
    enabled: sku.length > 0,
  });

export const reservationsQuery = () =>
  queryOptions({
    queryKey: reservationsKey,
    queryFn: () => api.listReservations(),
  });
