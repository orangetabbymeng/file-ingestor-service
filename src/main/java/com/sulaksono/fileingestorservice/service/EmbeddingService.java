package com.sulaksono.fileingestorservice.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.sulaksono.fileingestorservice.config.AzureOpenAIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Wraps calls to Azure OpenAI’s embedding endpoint.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final OpenAIClient client;
    private final AzureOpenAIProperties props;

    public EmbeddingService(OpenAIClient client, AzureOpenAIProperties props) {
        this.client = client;
        this.props = props;
    }

    public float[] generateEmbedding(String input) {

        EmbeddingsOptions options = new EmbeddingsOptions(List.of(input));

        Embeddings response = client.getEmbeddings(props.getEmbeddingModelDeployment(), options);

        List<Float> list = response.getData()
                .getFirst()          // first EmbeddingItem
                .getEmbedding();

        float[] vector = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            vector[i] = list.get(i);
        }

        log.debug("Received embedding vector of dimension {}", vector.length);
        return vector;
    }
}
