import type { ApiError, CreateReservationRequest, Reservation, StockLevel } from "./types";

const BASE = "/api/v1";

/** How a failed request should be treated by the optimistic-update layer. */
export type FailureKind = "conflict" | "validation" | "transient" | "timeout";

export class RequestError extends Error {
  readonly status: number;
  readonly body: ApiError | null;
  readonly kind: FailureKind;

  constructor(status: number, body: ApiError | null, kind: FailureKind) {
    super(body?.message ?? `request failed with ${status}`);
    this.name = "RequestError";
    this.status = status;
    this.body = body;
    this.kind = kind;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  idempotencyKey?: string;
  /** Abort the request after this many ms; a timeout is treated as "outcome unknown". */
  timeoutMs?: number;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, idempotencyKey, timeoutMs = 10_000 } = options;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch (cause) {
    clearTimeout(timer);
    if (cause instanceof DOMException && cause.name === "AbortError") {
      throw new RequestError(0, null, "timeout");
    }
    throw new RequestError(0, null, "transient"); // network error / server unreachable
  }
  clearTimeout(timer);

  if (response.ok) {
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
  }

  const errorBody = await safeJson(response);
  throw new RequestError(response.status, errorBody, classify(response.status));
}

function classify(status: number): FailureKind {
  if (status === 409) return "conflict";
  if (status === 400 || status === 422) return "validation";
  return "transient"; // 5xx and anything else — safe to retry with the same idempotency key
}

async function safeJson(response: Response): Promise<ApiError | null> {
  try {
    return (await response.json()) as ApiError;
  } catch {
    return null;
  }
}

export const api = {
  getStock: (sku: string) => request<StockLevel>(`/stock/${sku}`),
  listReservations: () => request<Reservation[]>(`/reservations?limit=50`),
  createReservation: (input: CreateReservationRequest) =>
    request<Reservation>(`/reservations`, {
      method: "POST",
      body: input,
      idempotencyKey: input.idempotencyKey,
    }),
  releaseReservation: (id: string) =>
    request<Reservation>(`/reservations/${id}`, { method: "DELETE" }),
  openStock: (sku: string, onHand: number) =>
    request<StockLevel>(`/stock`, { method: "POST", body: { sku, onHand } }),
};
