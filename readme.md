# File-Ingestor Service

Spring Boot 3.5.6 micro-service whose sole responsibility is to

1. Receive source / diagram / markdown files through a REST endpoint (single or zipped batch uploads).
2. Persist the raw uploads to disk.
3. Asynchronously create Azure OpenAI text-embeddings for each file.
4. Store the resulting vectors in PostgreSQL pgvector (schema `"ingestor-db"`).

The vectors are ready for consumption by downstream components such as **o3-assistant**.

Contact: ashar[dot]sulaksono[at]gmail[dot]com

---

## Features

* Spring Boot 3.5 (Java 21)
* Asynchronous processing with `@Async` and a tuned thread pool
* Azure OpenAI Embedding API (SDK 1.0.0-beta.8)
* Native pgvector persistence via JPA `AttributeConverter`
* OpenAPI 3 documentation and Swagger-UI (`/swagger-ui.html`)
* Docker-free local start (PostgreSQL must be running)
* Some unit tests (JUnit 5 + Mockito)

---

## Requirements

| Tool            | Version                                               |
|-----------------|-------------------------------------------------------|
| Java            | 21+                                                   |
| Maven           | 3.9+                                                  |
| PostgreSQL      | 15+ (with pgvector)                                   |
| Azure OpenAI    | any resource w/ **text-embedding-ada-002** deployment |

### Database preparation

```sql
-- enable extension once per cluster
CREATE EXTENSION IF NOT EXISTS pgvector;

-- run the schema bootstrap (executed automatically on app start too)
\i src/main/resources/schema.sql
```

Ensure the user referenced in `application.yaml` has permission to the schema `"ingestor-db"`.

---

## Configuration

All parameters live in `src/main/resources/application.yaml`.  
Override in production via environment variables:

| Property                              | Env Var                       | Example                                             |
|---------------------------------------|-------------------------------|-----------------------------------------------------|
| `spring.datasource.password`          | `SPRING_DATASOURCE_PASSWORD`  | `export SPRING_DATASOURCE_PASSWORD=super-secret`    |
| `azure.openai.api-key`                | `AZURE_OPENAI_API_KEY`        | `export AZURE_OPENAI_API_KEY=<key>`                 |
| `azure.openai.endpoint`               | `AZURE_OPENAI_ENDPOINT`       | `https://my-openai-resource.openai.azure.com/`      |
| `storage.location`                    | `STORAGE_LOCATION`            | `/var/data/uploads`                                 |

---

## Running the service

```bash
# compile & start
mvn spring-boot:run
```

The service listens on **http://localhost:8080**

* OpenAPI JSON: `http://localhost:8080/v3/api-docs`
* Swagger-UI   : `http://localhost:8080/swagger-ui.html`

---

## API

### `POST /api/files/upload`

Multipart form-data with one or more parts named **files**.

Supported extensions  
`*.java`, `.sql`, `*.mmd`, `*.mermaid`, `*.puml`, `*.plantuml`, `*.drawio`, `*.dio`, `*.md`, `*.markdown`, `*.txt`, and `.zip` archives containing any of the above.

Response `202 Accepted`

```json
{
  "accepted": ["design.puml", "spec.zip"],
  "rejected": ["virus.exe (extension not allowed)"]
}
```

Embeddings are generated in the background; logs show success / failure.

---

## Build & Tests

```bash
# run the full test suite
mvn verify

# create an executable jar
mvn clean package
```

---

## Troubleshooting

* **“column is of type vector but expression is …”**  
  Ensure you are using the provided `FloatArrayVectorConverter` and your JDBC URL contains `stringtype=unspecified`.

* **Large files**  
  Tune `spring.servlet.multipart.max-file-size` & `max-request-size` in `application.yaml`.

---

## License

MIT – use at your own risk.  
Embeddings produced via Azure OpenAI are subject to Microsoft’s Responsible AI guidelines.