package com.sulaksono.fileingestorservice.repository;

import com.sulaksono.fileingestorservice.model.FileEmbedding;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Basic CRUD repository – further vector-similarity queries can be added later.
 */

public interface FileEmbeddingRepository extends CrudRepository<FileEmbedding, UUID> {

    @Modifying @Transactional
    @Query("update FileEmbedding f set f.deprecated = :flag where f.module = :module")
    int markDeprecatedByModule(String module, boolean flag);

    @Modifying @Transactional
    void deleteByModule(String module);

    /* fetch rows for re-embedding */
    List<FileEmbedding> findByModule(String module);
}