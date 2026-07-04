package com.sulaksono.fileingestorservice.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.sulaksono.fileingestorservice.config.AzureOpenAIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    public EmbeddingService(OpenAIClient client,
                            AzureOpenAIProperties props) {
        this.client = client;
        this.props = props;
    }

    public float[] generateEmbedding(String input) {

        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input cannot be blank");
        }

        String requestId = MDC.get("requestId");

        log.debug(
                "event=embedding_start requestId={} inputLength={}",
                requestId,
                input.length());

        try {
            Embeddings response = client.getEmbeddings(
                    props.getEmbeddingModelDeployment(),
                    new EmbeddingsOptions(List.of(input))
            );

            if (response.getData() == null || response.getData().isEmpty()) {
                throw new IllegalStateException(
                        "Azure OpenAI returned no embeddings");
            }

            List<Float> embedding =
                    response.getData().getFirst().getEmbedding();

            float[] vector = new float[embedding.size()];

            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i);
            }

            log.info(
                    "event=embedding_success requestId={} dimension={}",
                    requestId,
                    vector.length);

            return vector;

        } catch (Exception e) {
            log.error(
                    "event=embedding_error requestId={} message={}",
                    requestId,
                    e.getMessage(),
                    e);

            throw e;
        }
    }
}
