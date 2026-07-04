package com.sulaksono.fileingestorservice.model;

import com.sulaksono.fileingestorservice.model.converter.FloatArrayVectorConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "file_embeddings", schema = "engineering_reference")
public class FileEmbedding {

    @Id
    @GeneratedValue
    private UUID id;

    private String fileName;

    private String path;
    private String module;

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

    private int chunkIdx;
    private int chunkOf;

    private String moduleVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canonical_file_id", nullable = false)
    private CanonicalFile canonicalFile;

    public FileEmbedding() {}

    public FileEmbedding(String fileName,
                         String path,
                         String module,
                         int chunkIdx,
                         int chunkOf,
                         FileType fileType,
                         float[] embedding,
                         String content,
                         String moduleVersion,
                         CanonicalFile canonicalFile) {
        this.fileName = fileName;
        this.path = path;
        this.module = module;
        this.chunkIdx = chunkIdx;
        this.chunkOf = chunkOf;
        this.fileType = fileType;
        this.embedding = embedding;
        this.content = content;
        this.moduleVersion = moduleVersion;
        this.canonicalFile = canonicalFile;
    }
}