package com.sulaksono.fileingestorservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "canonical_files",
        schema = "engineering-reference",
        indexes = {
                @Index(name = "ix_canonical_files_path", columnList = "path"),
                @Index(name = "ix_canonical_files_module", columnList = "module"),
                @Index(name = "ix_canonical_files_repo", columnList = "repo_clone_url")
        }
)
public class CanonicalFile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 1024)
    private String path;

    @Column(nullable = false, length = 255)
    private String module;

    @Column(length = 255)
    private String moduleVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 255)
    private FileType fileType;

    @Column(nullable = false, length = 1024)
    private String repoCloneUrl;

    @Column(nullable = false, length = 255)
    private String repoRef = "master";

    @Column(nullable = false, length = 1024)
    private String pathInRepo;

    @Column(columnDefinition = "text")
    private String content; // nullable cache

    @Column(nullable = false)
    private boolean deprecated = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public CanonicalFile(String fileName,
                         String path,
                         String module,
                         String moduleVersion,
                         FileType fileType,
                         String repoCloneUrl,
                         String repoRef,
                         String pathInRepo,
                         String content) {
        this.fileName = fileName;
        this.path = path;
        this.module = module;
        this.moduleVersion = moduleVersion;
        this.fileType = fileType;
        this.repoCloneUrl = repoCloneUrl;
        this.repoRef = (repoRef == null || repoRef.isBlank()) ? "master" : repoRef;
        this.pathInRepo = pathInRepo;
        this.content = content;
    }
}