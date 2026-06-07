package com.sulaksono.fileingestorservice.repository;

import com.sulaksono.fileingestorservice.model.CanonicalFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CanonicalFileRepository extends JpaRepository<CanonicalFile, UUID> {

    Optional<CanonicalFile> findByModuleAndModuleVersionAndPath(
            String module,
            String moduleVersion,
            String path
    );

    /**
     * NULL-safe lookup (matches the recommended unique index using COALESCE(module_version,''))
     */
    @Query("""
           select c
           from CanonicalFile c
           where c.module = :module
             and coalesce(c.moduleVersion, '') = coalesce(:moduleVersion, '')
             and c.path = :path
           """)
    Optional<CanonicalFile> findByModuleAndModuleVersionAndPathNullSafe(
            @Param("module") String module,
            @Param("moduleVersion") String moduleVersion,
            @Param("path") String path
    );

    boolean existsByModuleAndModuleVersionAndPath(String module, String moduleVersion, String path);

    void deleteByModuleAndModuleVersionAndPath(String module, String moduleVersion, String path);

    /**
     * Optional: mark a canonical file as deprecated (soft-delete style).
     */
    @Modifying
    @Query("""
           update CanonicalFile c
              set c.deprecated = true
            where c.module = :module
              and coalesce(c.moduleVersion, '') = coalesce(:moduleVersion, '')
              and c.path = :path
           """)
    int markDeprecated(
            @Param("module") String module,
            @Param("moduleVersion") String moduleVersion,
            @Param("path") String path
    );

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByModule(String module);
}