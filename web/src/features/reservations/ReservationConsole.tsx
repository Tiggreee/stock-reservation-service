import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { Reservation } from "../../api/types";
import { useOpenStock } from "../stock/useOpenStock";
import { reservationsQuery, stockQuery } from "./queries";
import { useReleaseReservation } from "./useReleaseReservation";
import { useReserveStock } from "./useReserveStock";

function newIdempotencyKey() {
  return crypto.randomUUID();
}

export function ReservationConsole() {
  const [sku, setSku] = useState("DEMO-1");
  const [quantity, setQuantity] = useState(1);
  const [seedQty, setSeedQty] = useState(10);

  const stock = useQuery(stockQuery(sku));
  const reservations = useQuery(reservationsQuery());
  const reserve = useReserveStock(sku);
  const release = useReleaseReservation();
  const openStock = useOpenStock();

  const forSku = (reservations.data ?? []).filter((r) => r.sku === sku);

  return (
    <main className="console">
      <header>
        <h1>Stock Reservation Console</h1>
        <p className="subtitle">Optimistic reserve, with snapshot rollback on failure.</p>
      </header>

      <section className="panel">
        <label>
          SKU
          <input value={sku} onChange={(e) => setSku(e.target.value.trim())} />
        </label>

        <div className="stock-readout">
          {stock.isPending && sku ? (
            <span className="muted">loading…</span>
          ) : stock.isError ? (
            <span className="muted">
              no stock record —
              <input
                type="number"
                min={0}
                value={seedQty}
                onChange={(e) => setSeedQty(Number(e.target.value))}
              />
              <button
                onClick={() => openStock.mutate({ sku, onHand: seedQty })}
                disabled={openStock.isPending || !sku}
              >
                open with {seedQty}
              </button>
            </span>
          ) : stock.data ? (
            <dl>
              <div>
                <dt>available</dt>
                <dd className={stock.data.available === 0 ? "zero" : ""}>{stock.data.available}</dd>
              </div>
              <div>
                <dt>reserved</dt>
                <dd>{stock.data.reserved}</dd>
              </div>
              <div>
                <dt>on hand</dt>
                <dd>{stock.data.onHand}</dd>
              </div>
            </dl>
          ) : null}
        </div>
      </section>

      <section className="panel">
        <label>
          quantity
          <input
            type="number"
            min={1}
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
          />
        </label>
        <button
          className="primary"
          disabled={reserve.isPending || !stock.data}
          onClick={() =>
            reserve.mutate({ sku, quantity, idempotencyKey: newIdempotencyKey() })
          }
        >
          {reserve.isPending ? "Reserving…" : `Reserve ${quantity}`}
        </button>
      </section>

      <section className="panel">
        <h2>Reservations for {sku}</h2>
        {forSku.length === 0 ? (
          <p className="muted">none yet</p>
        ) : (
          <ul className="reservation-list">
            {forSku.map((r) => (
              <ReservationRow
                key={r.id}
                reservation={r}
                onRelease={() => release.mutate(r)}
                releasing={release.isPending && release.variables?.id === r.id}
              />
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

function ReservationRow({
  reservation,
  onRelease,
  releasing,
}: {
  reservation: Reservation;
  onRelease: () => void;
  releasing: boolean;
}) {
  const optimistic = reservation.id.startsWith("optimistic-");
  return (
    <li className={optimistic ? "optimistic" : ""}>
      <span className="qty">{reservation.quantity}</span>
      <span className={`status status-${reservation.status.toLowerCase()}`}>
        {optimistic ? "pending…" : reservation.status.toLowerCase()}
      </span>
      <code className="id">{reservation.id.slice(0, 8)}</code>
      {reservation.status === "PENDING" && !optimistic && (
        <button onClick={onRelease} disabled={releasing}>
          {releasing ? "releasing…" : "release"}
        </button>
      )}
    </li>
  );
}
