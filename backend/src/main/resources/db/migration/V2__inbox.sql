-- Consumer-side idempotency: the id of every Kafka event we have applied.

create table inbox_event (
    event_id     varchar(128) primary key,
    event_type   varchar(128) not null,
    processed_at timestamptz  not null
);
