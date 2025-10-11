package com.sulaksono.fileingestorservice.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.sulaksono.fileingestorservice.config.AzureOpenAIProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test verifying that EmbeddingService converts the Azure response to a float[].
 */
class EmbeddingServiceTest {

    @Test
    void generateEmbeddingConvertsListToArray() {
        /* ---- Arrange --------------------------------------------------- */
        OpenAIClient client = mock(OpenAIClient.class);
        Embeddings     embeddings = mock(Embeddings.class);
        EmbeddingItem  item       = mock(EmbeddingItem.class);

        // stub the SDK getters
        when(item.getEmbedding()).thenReturn(List.of(0.1f, 0.2f));
        when(embeddings.getData()).thenReturn(List.of(item));
        when(client.getEmbeddings(anyString(), any())).thenReturn(embeddings);

        AzureOpenAIProperties props = new AzureOpenAIProperties();
        props.setEndpoint("dummy");
        props.setApiKey("dummy");
        props.setEmbeddingModelDeployment("test-deploy");

        EmbeddingService service = new EmbeddingService(client, props);

        /* ---- Act ------------------------------------------------------- */
        float[] result = service.generateEmbedding("hello world");

        /* ---- Assert ---------------------------------------------------- */
        assertThat(result).containsExactly(0.1f, 0.2f);
    }
}