package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.model.CanonicalFile;
import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReembeddingServiceTest {

    private final FileEmbeddingRepository repo = mock(FileEmbeddingRepository.class);
    private final EmbeddingService embedSvc = mock(EmbeddingService.class);

    private ReembeddingService service() {
        return new ReembeddingService(repo, embedSvc);
    }

    @Test
    void reembedModule_shouldReembedAndSaveSingleRow() {
        FileEmbedding row = row(
                "Demo.java",
                "src/Demo.java",
                "demo-module",
                FileType.JAVA,
                "class Demo {}"
        );

        when(repo.findByModule("demo-module"))
                .thenReturn(List.of(row));

        when(embedSvc.generateEmbedding(anyString()))
                .thenReturn(new float[]{0.1f, 0.2f});

        service().reembedModule("demo-module");

        ArgumentCaptor<String> embeddingInputCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(embedSvc).generateEmbedding(embeddingInputCaptor.capture());

        assertThat(embeddingInputCaptor.getValue())
                .contains("### path: src/Demo.java")
                .contains("### type: JAVA")
                .contains("### module: demo-module")
                .contains("### deprecated: false")
                .contains("class Demo {}");

        assertThat(row.getEmbedding())
                .containsExactly(0.1f, 0.2f);

        verify(repo).save(row);
    }

    @Test
    void reembedModule_shouldProcessMultipleRows() {
        FileEmbedding first = row(
                "A.java",
                "src/A.java",
                "demo-module",
                FileType.JAVA,
                "class A {}"
        );

        FileEmbedding second = row(
                "B.java",
                "src/B.java",
                "demo-module",
                FileType.JAVA,
                "class B {}"
        );

        when(repo.findByModule("demo-module"))
                .thenReturn(List.of(first, second));

        when(embedSvc.generateEmbedding(anyString()))
                .thenReturn(new float[]{1.0f});

        service().reembedModule("demo-module");

        verify(embedSvc, times(2)).generateEmbedding(anyString());

        verify(repo).save(first);
        verify(repo).save(second);

        assertThat(first.getEmbedding())
                .containsExactly(1.0f);

        assertThat(second.getEmbedding())
                .containsExactly(1.0f);
    }

    @Test
    void reembedModule_whenNoRows_shouldNotCallEmbeddingService() {
        when(repo.findByModule("demo-module"))
                .thenReturn(List.of());

        service().reembedModule("demo-module");

        verify(repo).findByModule("demo-module");
        verifyNoInteractions(embedSvc);
        verify(repo, never()).save(any());
    }

    @Test
    void reembedModule_whenRepositoryReturnsNull_shouldNotCallEmbeddingService() {
        when(repo.findByModule("demo-module"))
                .thenReturn(null);

        service().reembedModule("demo-module");

        verify(repo).findByModule("demo-module");
        verifyNoInteractions(embedSvc);
        verify(repo, never()).save(any());
    }

    @Test
    void reembedModule_whenOneRowFails_shouldContinueWithNextRow() {
        FileEmbedding first = row(
                "A.java",
                "src/A.java",
                "demo-module",
                FileType.JAVA,
                "class A {}"
        );

        FileEmbedding second = row(
                "B.java",
                "src/B.java",
                "demo-module",
                FileType.JAVA,
                "class B {}"
        );

        when(repo.findByModule("demo-module"))
                .thenReturn(List.of(first, second));

        when(embedSvc.generateEmbedding(anyString()))
                .thenThrow(new RuntimeException("embedding failed"))
                .thenReturn(new float[]{2.0f});

        service().reembedModule("demo-module");

        verify(embedSvc, times(2)).generateEmbedding(anyString());

        verify(repo, never()).save(first);
        verify(repo).save(second);

        assertThat(first.getEmbedding())
                .isNull();

        assertThat(second.getEmbedding())
                .containsExactly(2.0f);
    }

    @Test
    void reembedModule_whenRepositoryFails_shouldThrow() {
        RuntimeException failure = new RuntimeException("db down");

        when(repo.findByModule("demo-module"))
                .thenThrow(failure);

        assertThatThrownBy(() -> service().reembedModule("demo-module"))
                .isSameAs(failure);

        verifyNoInteractions(embedSvc);
        verify(repo, never()).save(any());
    }

    @Test
    void reembedModule_blankModule_shouldThrow() {
        assertThatThrownBy(() -> service().reembedModule(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module");

        verifyNoInteractions(repo, embedSvc);
    }

    @Test
    void reembedModule_nullModule_shouldThrow() {
        assertThatThrownBy(() -> service().reembedModule(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module");

        verifyNoInteractions(repo, embedSvc);
    }

    @Test
    void reembedModule_nullRow_shouldSkipAndContinue() {
        FileEmbedding valid = row(
                "Demo.java",
                "src/Demo.java",
                "demo-module",
                FileType.JAVA,
                "class Demo {}"
        );

        when(repo.findByModule("demo-module"))
                .thenReturn(Arrays.asList(null, valid));

        when(embedSvc.generateEmbedding(anyString()))
                .thenReturn(new float[]{0.7f});

        service().reembedModule("demo-module");

        verify(embedSvc, times(1)).generateEmbedding(anyString());
        verify(repo).save(valid);

        assertThat(valid.getEmbedding())
                .containsExactly(0.7f);
    }

    private static FileEmbedding row(
            String fileName,
            String path,
            String module,
            FileType fileType,
            String content) {

        return new FileEmbedding(
                fileName,
                path,
                module,
                0,
                1,
                fileType,
                null,
                content,
                "1.0.0",
                new CanonicalFile()
        );
    }
}