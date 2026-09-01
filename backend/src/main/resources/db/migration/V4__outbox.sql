-- Transactional outbox: domain events staged in the same transaction as the
-- change that produced them, then relayed to Kafka.

create table outbox_event (
    id             uuid          primary key,
    aggregate_type varchar(64)   not null,
    aggregate_id   varchar(128)  not null,
    event_type     varchar(128)  not null,
    payload        jsonb         not null,
    created_at     timestamptz   not null,
    published_at   timestamptz
);

create index ix_outbox_unpublished
    on outbox_event (created_at)
    where published_at is null;
