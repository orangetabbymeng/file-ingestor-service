package com.sulaksono.fileingestorservice.model;

import com.sulaksono.fileingestorservice.model.converter.FloatArrayVectorConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to table file_embeddings with a pgvector column.
 */
@Getter
@Entity
@Table(
        name = "file_embeddings",
        schema = "ingestor_db"
)
public class FileEmbedding {

    @Id
    @GeneratedValue
    private UUID id;

    private String fileName;

    private String path;      //  e.g. src/main/resources/application.yaml
    private String module;    //  e.g. order-service

    @Enumerated(EnumType.STRING)
    private FileType fileType;

    @Setter
    @Column(columnDefinition = "vector")
    @Convert(converter = FloatArrayVectorConverter.class)
    private float[] embedding;

    @CreationTimestamp
    private Instant createdAt;

    @Column(columnDefinition = "text")
    private String content;

    private boolean deprecated = false;

    protected FileEmbedding() { }

    public FileEmbedding(String fileName,
                         String path,
                         String module,
                         FileType fileType,
                         float[]  embedding,
                         String   content,
                         boolean  deprecated) {   // <─ new parameter
        this.fileName  = fileName;
        this.path      = path;
        this.module    = module;
        this.fileType  = fileType;
        this.embedding = embedding;
        this.content   = content;
        this.deprecated= deprecated;
    }

}
