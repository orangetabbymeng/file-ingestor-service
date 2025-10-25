package com.sulaksono.fileingestorservice.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.okhttp.OkHttpAsyncHttpClientBuilder;
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
    public HttpClient okHttpClient() {
        return new OkHttpAsyncHttpClientBuilder()
                .build();
    }

    @Bean
    public OpenAIClient openAIClient(
            AzureOpenAIProperties props,
            HttpClient okHttpClient) {

        return new OpenAIClientBuilder()
                .endpoint(props.getEndpoint())
                .credential(new AzureKeyCredential(props.getApiKey()))
                .httpClient(okHttpClient)
                .buildClient();
    }


}