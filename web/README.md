# Frontend — stock reservation console

React 19 · TypeScript (strict) · Vite · TanStack Query v5

A small console for reserving stock against a SKU. The reserve and release
actions are **optimistic**: the UI updates the moment the button is pressed and
reconciles with the server afterwards. Every failure path restores the exact
prior state from a snapshot.

## Run

```bash
npm install
npm run dev          # proxies /api and /actuator to http://localhost:8080
```

## Checks

```bash
npm run lint
npm run typecheck
npm run test         # Vitest + MSW: optimistic write, rollback, retry, reconcile
npm run build
```

## Where the interesting code is

| File | What it does |
|------|--------------|
| [`src/features/reservations/useReserveStock.ts`](src/features/reservations/useReserveStock.ts) | The optimistic mutation: `onMutate` snapshot + write, `onError` rollback + classify, `onSettled` reconcile |
| [`src/api/client.ts`](src/api/client.ts) | `fetch` wrapper that classifies failures into `conflict` / `validation` / `transient` / `timeout` |
| [`../docs/frontend/optimistic-updates.md`](../docs/frontend/optimistic-updates.md) | Snapshot handling and the rollback decision table |
