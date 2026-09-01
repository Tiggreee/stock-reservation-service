-- Records that exhausted their retries or hit a permanent error, kept for
-- inspection and redrive.

create table dead_letter (
    id             uuid          primary key,
    topic          varchar(128)  not null,
    partition_no   integer,
    kafka_offset   bigint,
    message_key    varchar(256),
    payload        text          not null,
    exception_type varchar(256),
    exception_msg  text,
    failed_at      timestamptz   not null,
    redriven_at    timestamptz
);

create index ix_dead_letter_unredriven on dead_letter (failed_at) where redriven_at is null;
