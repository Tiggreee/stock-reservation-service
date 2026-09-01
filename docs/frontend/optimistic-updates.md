# Optimistic updates — snapshots and rollback

The reserve action must feel instant. The UI decrements available stock and
shows a pending reservation the moment the button is pressed, then reconciles
with the server. This document describes what is snapshotted, why, and how each
failure is handled.

Implementation: [`web/src/features/reservations/useReserveStock.ts`](../../web/src/features/reservations/useReserveStock.ts).

## The mutation lifecycle

```
click Reserve
   │
   ▼
onMutate ──► cancelQueries(stock, reservations)     # stop in-flight refetches
         ──► snapshot both caches into context
         ──► optimistic write: available -= qty, prepend a PENDING row
   │
   ▼
POST /api/v1/reservations   (Idempotency-Key = the key generated for this intent)
   │
   ├─ 201 ─────────────► onSettled ─► invalidate both ─► reconcile with server
   │
   ├─ 409 ─────────────► onError ─► restore snapshot ─► "sold out" / "busy", no retry
   │
   ├─ 400 / 422 ───────► onError ─► restore snapshot ─► field / validation message, no retry
   │
   └─ 500 / network ───► onError ─► restore snapshot ─► toast + retry (×3, backoff),
                                                        same Idempotency-Key
      (timeout — request aborted) ─► same as transient, then onSettled's
                                     invalidate reveals whether it actually landed
```

## What is snapshotted, and why

A single reserve changes **two** cache entries:

- `["stock", sku]` — the SKU's `available` count
- `["reservations"]` — the list the console renders

So `onMutate` snapshots **both** via `getQueryData` and stores them in the
mutation's `context`. `onError` restores both. Restoring only one would leave
the UI internally inconsistent (e.g. stock back to normal but a phantom pending
row still showing).

`cancelQueries` runs **first**, before the optimistic write. Without it, a
background refetch that was already in flight could resolve *after* the rollback
and re-apply stale data, undoing the restore.

`onSettled` runs on **every** outcome — success, error, and the aborted-timeout
case — and invalidates both queries. The optimistic write is always a bet; the
server is the source of record, and `onSettled` is where the client converges
back onto it.

## Rollback decision table

| Failure | Signal | Cache action | User sees | Auto-retry |
|---------|--------|--------------|-----------|------------|
| Server / network error | `500`, or no response | full rollback to snapshot | toast: "Couldn't reserve — stock restored. Retrying…" | **yes** — ×3, exponential backoff, same `Idempotency-Key` |
| Stock gone | `409 INSUFFICIENT_STOCK` | rollback | inline: "Just sold out — someone reserved the last unit." | no |
| SKU contended | `409 STOCK_CONTENDED` | rollback | "That SKU is busy right now — try again." | no (a manual retry is safe) |
| Validation | `400` / `422` | rollback | field / message error | no |
| Timeout — outcome unknown | request aborted | rollback, then `invalidate` | "Status unknown — refreshing to check." | no; the invalidation reveals the truth, and the idempotency key makes a manual retry safe |

## Idempotency

`idempotencyKey` is generated once per user intent (`crypto.randomUUID()`) and
reused on every retry of that mutation. The backend enforces a unique constraint
on it, so:

- a retry after a 500 the server actually processed returns the **original**
  reservation instead of booking a second one;
- if a client perceives a timeout but the write landed, `onSettled`'s
  invalidation surfaces the real reservation and a user-driven retry is still
  safe.

## Concurrent mutations

Same-SKU reserve mutations share a `scope` (`reserve:<sku>`), so TanStack Query
runs them one at a time. This keeps the snapshot/rollback chain coherent — two
interleaved optimistic writes against the same cache entry would otherwise be
able to restore each other's stale snapshot.
