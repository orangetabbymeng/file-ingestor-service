package com.sulaksono.fileingestorservice.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vector")
public class VectorProperties {

    /**
     * How many path segments (from the right) to include in the header.
     * 0 ➜ file name only.
     */
    @Min(0)
    private int includePathDepth = 0;

    private int chunkSizeTokens    = 800;   // window size
    private int chunkOverlapTokens = 100;   // overlap
}