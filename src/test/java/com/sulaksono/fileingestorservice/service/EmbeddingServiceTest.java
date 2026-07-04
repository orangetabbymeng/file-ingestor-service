package com.sulaksono.fileingestorservice.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.sulaksono.fileingestorservice.config.AzureOpenAIProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    private static final String DEPLOYMENT_NAME = "text-embedding-3-large";

    @Mock
    private OpenAIClient client;

    @Mock
    private AzureOpenAIProperties props;

    @Mock
    private Embeddings embeddings;

    @Mock
    private EmbeddingItem embeddingItem;

    @InjectMocks
    private EmbeddingService service;

    @Test
    void generateEmbedding_returnsVector() {
        when(props.getEmbeddingModelDeployment())
                .thenReturn(DEPLOYMENT_NAME);

        when(client.getEmbeddings(any(), any()))
                .thenReturn(embeddings);

        when(embeddings.getData())
                .thenReturn(List.of(embeddingItem));

        when(embeddingItem.getEmbedding())
                .thenReturn(List.of(1.0f, 2.0f, 3.0f));

        float[] result = service.generateEmbedding("hello world");

        assertThat(result)
                .containsExactly(1.0f, 2.0f, 3.0f);

        verify(client).getEmbeddings(
                eq(DEPLOYMENT_NAME),
                any(EmbeddingsOptions.class)
        );
    }

    @Test
    void generateEmbedding_passesInputToAzure() {
        when(props.getEmbeddingModelDeployment())
                .thenReturn(DEPLOYMENT_NAME);

        when(client.getEmbeddings(any(), any()))
                .thenReturn(embeddings);

        when(embeddings.getData())
                .thenReturn(List.of(embeddingItem));

        when(embeddingItem.getEmbedding())
                .thenReturn(List.of(1.0f));

        service.generateEmbedding("sample text");

        ArgumentCaptor<EmbeddingsOptions> captor =
                ArgumentCaptor.forClass(EmbeddingsOptions.class);

        verify(client).getEmbeddings(
                eq(DEPLOYMENT_NAME),
                captor.capture()
        );

        assertThat(captor.getValue().getInput())
                .containsExactly("sample text");
    }

    @Test
    void generateEmbedding_blankInput_throwsException() {
        assertThatThrownBy(() -> service.generateEmbedding(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");

        verifyNoInteractions(client, props);
    }

    @Test
    void generateEmbedding_nullInput_throwsException() {
        assertThatThrownBy(() -> service.generateEmbedding(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");

        verifyNoInteractions(client, props);
    }

    @Test
    void generateEmbedding_noEmbeddingReturned_throwsException() {
        when(props.getEmbeddingModelDeployment())
                .thenReturn(DEPLOYMENT_NAME);

        when(client.getEmbeddings(any(), any()))
                .thenReturn(embeddings);

        when(embeddings.getData())
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.generateEmbedding("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no embeddings");

        verify(client).getEmbeddings(
                eq(DEPLOYMENT_NAME),
                any(EmbeddingsOptions.class)
        );
    }

    @Test
    void generateEmbedding_nullEmbeddingData_throwsException() {
        when(props.getEmbeddingModelDeployment())
                .thenReturn(DEPLOYMENT_NAME);

        when(client.getEmbeddings(any(), any()))
                .thenReturn(embeddings);

        when(embeddings.getData())
                .thenReturn(null);

        assertThatThrownBy(() -> service.generateEmbedding("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no embeddings");

        verify(client).getEmbeddings(
                eq(DEPLOYMENT_NAME),
                any(EmbeddingsOptions.class)
        );
    }

    @Test
    void generateEmbedding_clientThrows_propagatesException() {
        RuntimeException ex = new RuntimeException("azure failure");

        when(props.getEmbeddingModelDeployment())
                .thenReturn(DEPLOYMENT_NAME);

        when(client.getEmbeddings(any(), any()))
                .thenThrow(ex);

        assertThatThrownBy(() -> service.generateEmbedding("hello"))
                .isSameAs(ex);
    }
}