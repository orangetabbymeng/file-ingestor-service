/* bootstrap DDL – pgvector + metadata columns */
create schema if not exists ingestor_db;

create extension if not exists pgcrypto;
create extension if not exists vector;

create table canonical_files
(
    id             uuid                     default gen_random_uuid() not null
        primary key,
    file_name      varchar(255)                                       not null,
    path           varchar(1024)                                      not null,
    module         varchar(255)                                       not null,
    module_version varchar(255),
    file_type      varchar(255)                                       not null,
    repo_clone_url varchar(1024),
    repo_ref       varchar(255)             default 'master'::character varying,
    path_in_repo   varchar(1024),
    content        text,
    deprecated     boolean                  default false             not null,
    created_at     timestamp with time zone default CURRENT_TIMESTAMP not null,
    updated_at     timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index if not exists file_embeddings_embedding_idx
    on ingestor_db.file_embeddings
        using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);

create table file_embeddings
(
    id                uuid                     default gen_random_uuid() not null
        primary key,
    file_name         varchar(255)                                       not null,
    path              varchar(255)                                       not null,
    module            varchar(255)                                       not null,
    file_type         varchar(255)                                       not null,
    embedding         vector(1536)                                       not null,
    content           text                                               not null,
    deprecated        boolean                  default false,
    created_at        timestamp with time zone default CURRENT_TIMESTAMP,
    chunk_idx         integer,
    chunk_of          integer,
    module_version    varchar(255),
    canonical_file_id uuid
        constraint fk_file_embeddings_canonical_file
            references canonical_files
);

CREATE INDEX file_embeddings_embedding_idx
    ON file_embeddings USING ivfflat (embedding vector_cosine_ops);

create index ix_file_embeddings_canonical_file_id
    on file_embeddings (canonical_file_id);

create unique index uq_canonical_files_mod_ver_path
    on canonical_files (module, COALESCE(module_version, ''::character varying), path);

create index ix_canonical_files_path
    on canonical_files (path);

create index ix_canonical_files_module
    on canonical_files (module);

create index ix_canonical_files_repo
    on canonical_files (repo_clone_url);

create function set_updated_at() returns trigger
    language plpgsql
as
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_canonical_files_updated_at ON ingestor_db.canonical_files;

CREATE TRIGGER trg_canonical_files_updated_at
    BEFORE UPDATE ON ingestor_db.canonical_files
    FOR EACH ROW EXECUTE FUNCTION ingestor_db.set_updated_at();