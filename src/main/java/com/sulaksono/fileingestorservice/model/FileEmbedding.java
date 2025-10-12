package com.sulaksono.fileingestorservice.model;

import com.sulaksono.fileingestorservice.model.converter.FloatArrayVectorConverter;
import jakarta.persistence.*;
import lombok.Getter;
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
        schema = "\"ingestor_db\""
)
public class FileEmbedding {

    @Id
    @GeneratedValue
    private UUID id;

    private String fileName;

    @Enumerated(EnumType.STRING)
    private FileType fileType;

    /**
     * Vector column (pgvector). Conversion handled by {@link FloatArrayVectorConverter}.
     */
    @Column(columnDefinition = "vector")
    @Convert(converter = FloatArrayVectorConverter.class)
    private float[] embedding;

    @CreationTimestamp
    private Instant createdAt;

    @Lob
    @Column(columnDefinition = "text")
    private String content;

    public FileEmbedding(String fileName, FileType type, float[] embedding, String content) {
        this.fileName = fileName;
        this.fileType = type;
        this.embedding = embedding;
        this.content  = content;
    }

    // -- constructors --------------------------------------------------------

    protected FileEmbedding() { }

    public FileEmbedding(String fileName, FileType fileType, float[] embedding) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.embedding = embedding;
    }

}
