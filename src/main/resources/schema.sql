/* ---------------------------------------------------------------------------
   Bootstrap DDL for PostgreSQL + pgvector (manual creation).
   Target schema  :  "ingestor-db"
   Embedding size :  1536  (using text-embedding-ada-002 output)
---------------------------------------------------------------------------*/

-- 1. Enable required extensions (executed only once)
create extension if not exists pgcrypto;   -- gen_random_uuid()
create extension if not exists vector;     -- pgvector type

-- 2. Create (or keep) dedicated schema
create schema if not exists "ingestor-db";

-- 3. Create main table with a dimensioned vector column
create table if not exists "ingestor-db".file_embeddings (
                                                             id          uuid            primary key default gen_random_uuid(),
                                                             file_name   varchar(255)    not null,
                                                             file_type   varchar(30)     not null,
                                                             embedding   vector(1536)    not null,
                                                             created_at  timestamptz     default current_timestamp
);

-- 4. Similarity index (IVFFLAT)
--    The index must be built AFTER at least one row is present OR run with a populated table.
--    Requires the column be defined with an explicit dimension.
create index if not exists file_embeddings_embedding_idx
    on "ingestor-db".file_embeddings
        using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);