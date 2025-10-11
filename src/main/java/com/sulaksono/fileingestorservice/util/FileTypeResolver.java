package com.sulaksono.fileingestorservice.util;


import com.sulaksono.fileingestorservice.model.FileType;

/**
 * Thin wrapper around {@link FileType#fromFileName(String)} for readability.
 */
public final class FileTypeResolver {

    private FileTypeResolver() { }

    public static FileType resolve(String fileName) {
        return FileType.fromFileName(fileName);
    }
}