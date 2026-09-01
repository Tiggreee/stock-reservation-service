import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { ToastProvider } from "../../components/toast";
import { reservation, SKU, stock } from "../../mocks/handlers";
import { server } from "../../mocks/server";
import { reservationsQuery, stockKey, stockQuery } from "./queries";
import { useReserveStock } from "./useReserveStock";

function harness() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );

  // Render the queries too, so invalidate() actually refetches (as it would in the console).
  const view = renderHook(
    () => ({
      reserve: useReserveStock(SKU),
      stock: useQuery(stockQuery(SKU)),
      reservations: useQuery(reservationsQuery()),
    }),
    { wrapper },
  );
  return { queryClient, ...view };
}

const availableInCache = (qc: QueryClient) =>
  qc.getQueryData<ReturnType<typeof stock>>(stockKey(SKU))?.available;

describe("useReserveStock", () => {
  it("applies an optimistic decrement immediately and reconciles on success", async () => {
    server.use(
      http.post("/api/v1/reservations", async ({ request }) => {
        await delay(120); // keep the optimistic window observable
        const body = (await request.json()) as { quantity: number; idempotencyKey: string };
        return HttpResponse.json(
          reservation({ id: "srv-created", quantity: body.quantity, idempotencyKey: body.idempotencyKey }),
          { status: 201 },
        );
      }),
    );
    const { queryClient, result } = harness();
    await waitFor(() => expect(availableInCache(queryClient)).toBe(5)); // initial load

    result.current.reserve.mutate({ sku: SKU, quantity: 2, idempotencyKey: "key-ok" });

    await waitFor(() => expect(availableInCache(queryClient)).toBe(3)); // optimistic
    await waitFor(() => expect(result.current.reserve.isSuccess).toBe(true));
    // onSettled invalidates; the server (MSW default) still reports 5 on hand.
    await waitFor(() => expect(availableInCache(queryClient)).toBe(5));
  });

  it("rolls back to the snapshot and does not retry on a 409 conflict", async () => {
    server.use(
      http.post("/api/v1/reservations", () =>
        HttpResponse.json(
          { code: "INSUFFICIENT_STOCK", message: "sold out", timestamp: "", details: { available: 1 } },
          { status: 409 },
        ),
      ),
    );
    const { queryClient, result } = harness();
    await waitFor(() => expect(availableInCache(queryClient)).toBe(5));

    result.current.reserve.mutate({ sku: SKU, quantity: 4, idempotencyKey: "key-409" });

    await waitFor(() => expect(result.current.reserve.isError).toBe(true));
    await waitFor(() => expect(availableInCache(queryClient)).toBe(5)); // restored
    expect(result.current.reserve.failureCount).toBe(1); // no retry
  });

  it("retries a transient 500 with the same idempotency key, then succeeds", async () => {
    let attempts = 0;
    const seenKeys: string[] = [];
    server.use(
      http.post("/api/v1/reservations", async ({ request }) => {
        attempts += 1;
        const body = (await request.json()) as { idempotencyKey: string; quantity: number };
        seenKeys.push(body.idempotencyKey);
        if (attempts === 1) return new HttpResponse(null, { status: 500 });
        return HttpResponse.json(
          {
            id: "srv-retry",
            sku: SKU,
            quantity: body.quantity,
            status: "PENDING",
            idempotencyKey: body.idempotencyKey,
            orderRef: null,
            createdAt: "",
            expiresAt: "",
            settledAt: null,
          },
          { status: 201 },
        );
      }),
    );
    const { result } = harness();

    result.current.reserve.mutate({ sku: SKU, quantity: 1, idempotencyKey: "key-retry" });

    await waitFor(() => expect(result.current.reserve.isSuccess).toBe(true), { timeout: 5000 });
    expect(attempts).toBe(2);
    expect(new Set(seenKeys)).toEqual(new Set(["key-retry"])); // key reused across the retry
  });

  it("rolls back and reconciles after a persistent server error", async () => {
    server.use(
      http.post("/api/v1/reservations", () =>
        HttpResponse.json({ code: "INTERNAL_ERROR", message: "", timestamp: "", details: {} }, { status: 503 }),
      ),
    );
    const { queryClient, result } = harness();
    await waitFor(() => expect(availableInCache(queryClient)).toBe(5));

    result.current.reserve.mutate({ sku: SKU, quantity: 2, idempotencyKey: "key-500" });

    await waitFor(() => expect(availableInCache(queryClient)).toBe(3)); // optimistic first
    await waitFor(() => expect(result.current.reserve.isError).toBe(true), { timeout: 8000 });
    expect(result.current.reserve.failureCount).toBe(4); // 1 initial + 3 retries
    await waitFor(() => expect(availableInCache(queryClient)).toBe(5)); // reconciled back
  });
});
