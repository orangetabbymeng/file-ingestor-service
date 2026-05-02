package com.sulaksono.fileingestorservice.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.sulaksono.fileingestorservice.config.AzureOpenAIProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    @Test
    void generateEmbeddingConvertsListToArray() {
        // Arrange
        OpenAIClient client = mock(OpenAIClient.class);
        Embeddings embeddings = mock(Embeddings.class);
        EmbeddingItem item = mock(EmbeddingItem.class);

        when(item.getEmbedding()).thenReturn(List.of(0.1f, 0.2f));
        when(embeddings.getData()).thenReturn(List.of(item));
        when(client.getEmbeddings(eq("test-deploy"), any(EmbeddingsOptions.class)))
                .thenReturn(embeddings);

        AzureOpenAIProperties props = new AzureOpenAIProperties();
        props.setEmbeddingModelDeployment("test-deploy");

        EmbeddingService service = new EmbeddingService(client, props);

        // Act
        float[] result = service.generateEmbedding("hello world");

        // Assert
        assertThat(result).containsExactly(0.1f, 0.2f);
        verify(client).getEmbeddings(eq("test-deploy"), any(EmbeddingsOptions.class));
    }
}