package com.sulaksono.fileingestorservice.util;

import com.sulaksono.fileingestorservice.model.FileType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeResolverTest {

    @Test
    void resolve_javaFile_shouldReturnJava() {
        FileType result = FileTypeResolver.resolve("Demo.java");

        assertThat(result).isEqualTo(FileType.JAVA);
    }

    @Test
    void resolve_unknownExtension_shouldReturnUnknown() {
        FileType result = FileTypeResolver.resolve("notes.xyz");

        assertThat(result).isEqualTo(FileType.UNKNOWN);
    }

    @Test
    void resolve_nullFileName_shouldReturnUnknown() {
        FileType result = FileTypeResolver.resolve(null);

        assertThat(result).isEqualTo(FileType.UNKNOWN);
    }

    @Test
    void constructor_shouldBePrivate() throws Exception {
        Constructor<FileTypeResolver> constructor =
                FileTypeResolver.class.getDeclaredConstructor();

        assertThat(constructor.canAccess(null)).isFalse();

        constructor.setAccessible(true);
        FileTypeResolver instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }
}