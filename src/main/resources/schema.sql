/* bootstrap DDL – pgvector + metadata columns */
create schema if not exists ingestor_db;

create extension if not exists pgcrypto;
create extension if not exists vector;

create table if not exists ingestor_db.file_embeddings
(
    id          uuid            primary key default gen_random_uuid(),
    file_name   varchar(255)    not null,
    path        varchar(1024)   not null,
    module      varchar(128)    not null,
    file_type   varchar(30)     not null,
    embedding   vector(1536)    not null,
    content     text            not null,
    created_at  timestamptz     default current_timestamp
);

create index if not exists file_embeddings_embedding_idx
    on ingestor_db.file_embeddings
        using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);