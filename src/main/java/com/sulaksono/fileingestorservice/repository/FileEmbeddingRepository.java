package com.sulaksono.fileingestorservice.repository;

import com.sulaksono.fileingestorservice.model.FileEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Basic CRUD repository – further vector-similarity queries can be added later.
 */
public interface FileEmbeddingRepository extends JpaRepository<FileEmbedding, UUID> {
}
