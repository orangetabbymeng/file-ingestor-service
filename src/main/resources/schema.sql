/* bootstrap DDL – pgvector + metadata columns */
create schema if not exists ingestor_db;

create extension if not exists pgcrypto;
create extension if not exists vector;



create table if not exists ingestor_db.file_embeddings
(
    id          uuid          primary key default gen_random_uuid(),
    file_name   varchar(255)  not null,
    path        varchar(1024) not null,
    module      varchar(128)  not null,
    file_type   varchar(30)   not null,
    chunk_idx   int           not null,
    chunk_of    int           not null,
    embedding   vector(1536)  not null,
    content     text          not null,
    module_version varchar(50)        ,
    created_at  timestamptz   default current_timestamp
);


-- ALTER TABLE ingestor_db.file_embeddings
--     ADD COLUMN chunk_idx  int,
--     ADD COLUMN chunk_of   int;


create index if not exists file_embeddings_embedding_idx
    on ingestor_db.file_embeddings
        using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);