package com.sulaksono.fileingestorservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * storage.location property mapping.
 */
@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    @NotBlank
    private String location;

}
