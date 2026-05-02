package com.sulaksono.fileingestorservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer
    ) {
        String base = issuer + "/protocol/openid-connect";

        SecurityScheme keycloak = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows().authorizationCode(
                        new OAuthFlow()
                                .authorizationUrl(base + "/auth")
                                .tokenUrl(base + "/token")
                                .scopes(new Scopes())
                ));

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("keycloak", keycloak))
                .addSecurityItem(new SecurityRequirement().addList("keycloak"));
    }
}