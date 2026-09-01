import { http, HttpResponse } from "msw";
import type { Reservation, StockLevel } from "../api/types";

export const SKU = "SKU-TEST";

export function stock(overrides: Partial<StockLevel> = {}): StockLevel {
  return { sku: SKU, location: "MAIN", onHand: 5, reserved: 0, available: 5, version: 0, ...overrides };
}

export function reservation(overrides: Partial<Reservation> = {}): Reservation {
  return {
    id: "srv-1",
    sku: SKU,
    quantity: 1,
    status: "PENDING",
    idempotencyKey: "k",
    orderRef: null,
    createdAt: new Date().toISOString(),
    expiresAt: new Date().toISOString(),
    settledAt: null,
    ...overrides,
  };
}

/** Default happy-path handlers; individual tests override with server.use(). */
export const handlers = [
  http.get("/api/v1/stock/:sku", ({ params }) =>
    HttpResponse.json(stock({ sku: String(params.sku) })),
  ),
  http.get("/api/v1/reservations", () => HttpResponse.json([])),
  http.post("/api/v1/reservations", async ({ request }) => {
    const body = (await request.json()) as { sku: string; quantity: number; idempotencyKey: string };
    return HttpResponse.json(
      reservation({ id: "srv-created", quantity: body.quantity, idempotencyKey: body.idempotencyKey }),
      { status: 201 },
    );
  }),
];
