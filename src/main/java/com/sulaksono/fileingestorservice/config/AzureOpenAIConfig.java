package com.sulaksono.fileingestorservice.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a singleton {@link OpenAIClient}.
 */
@Configuration
@EnableConfigurationProperties(AzureOpenAIProperties.class)
public class AzureOpenAIConfig {

    @Bean
    public OpenAIClient openAIClient(AzureOpenAIProperties props) {
        return new OpenAIClientBuilder()
                .endpoint(props.getEndpoint())
                .credential(new AzureKeyCredential(props.getApiKey()))
                .buildClient();
    }
}