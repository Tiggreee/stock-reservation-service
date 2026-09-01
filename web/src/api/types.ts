// Mirrors the backend contract. In a larger setup these would be generated from
// the OpenAPI spec (openapi-typescript) so the two cannot drift.

export type ReservationStatus = "PENDING" | "CONFIRMED" | "RELEASED" | "EXPIRED";

export interface Reservation {
  id: string;
  sku: string;
  quantity: number;
  status: ReservationStatus;
  idempotencyKey: string;
  orderRef: string | null;
  createdAt: string;
  expiresAt: string;
  settledAt: string | null;
}

export interface StockLevel {
  sku: string;
  location: string;
  onHand: number;
  reserved: number;
  available: number;
  version: number;
}

export interface ApiError {
  code:
    | "INSUFFICIENT_STOCK"
    | "STOCK_CONTENDED"
    | "UNKNOWN_SKU"
    | "RESERVATION_NOT_FOUND"
    | "STOCK_RULE_VIOLATION"
    | "VALIDATION_FAILED"
    | "INTERNAL_ERROR"
    | string;
  message: string;
  timestamp: string;
  details: Record<string, unknown>;
}

export interface CreateReservationRequest {
  sku: string;
  quantity: number;
  idempotencyKey: string;
}
