-- Core stock and reservation tables.

create table stock_level (
    id             uuid          primary key,
    sku            varchar(64)   not null,
    location       varchar(64)   not null,
    on_hand        integer       not null,
    reserved       integer       not null,
    version        bigint        not null default 0,
    constraint uq_stock_level_sku_location unique (sku, location),
    constraint ck_stock_level_invariant
        check (reserved >= 0 and on_hand >= 0 and reserved <= on_hand)
);

create table reservation (
    id              uuid          primary key,
    sku             varchar(64)   not null,
    quantity        integer       not null,
    status          varchar(16)   not null,
    idempotency_key varchar(100)  not null,
    order_ref       varchar(128),
    created_at      timestamptz   not null,
    expires_at      timestamptz   not null,
    settled_at      timestamptz,
    version         bigint        not null default 0,
    constraint uq_reservation_idempotency_key unique (idempotency_key),
    constraint ck_reservation_quantity check (quantity > 0)
);

create index ix_reservation_status_expires on reservation (status, expires_at);
create index ix_reservation_sku on reservation (sku);

create table stock_ledger (
    id             uuid          primary key,
    sku            varchar(64)   not null,
    type           varchar(16)   not null,
    quantity_delta integer       not null,
    on_hand_after  integer       not null,
    reserved_after integer       not null,
    correlation_id varchar(128),
    created_at     timestamptz   not null
);

create index ix_stock_ledger_sku on stock_ledger (sku);
create index ix_stock_ledger_correlation on stock_ledger (correlation_id);
