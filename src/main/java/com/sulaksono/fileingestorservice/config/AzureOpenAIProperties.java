package com.sulaksono.fileingestorservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe mapping for azure.openai.* properties.
 */
@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "azure.openai")
public class AzureOpenAIProperties {

    @NotBlank
    private String endpoint;

    @NotBlank
    private String apiKey;

    @NotBlank
    private String embeddingModelDeployment;

    // -- getters / setters ---------------------------------------------------

}
