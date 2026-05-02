package com.sulaksono.fileingestorservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Upload: embedding-user + admins
                        .requestMatchers(HttpMethod.POST, "/api/upload/**")
                        .hasAnyRole("embedding-user", "embedding-admin", "assistant-admin")

                        // Everything else under /api/**: admins only
                        .requestMatchers("/api/**")
                        .hasAnyRole("embedding-admin", "assistant-admin")

                        // Non-API endpoints (health, swagger, etc.)
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(keycloakRolesConverter());
        return c;
    }

    /**
     * Maps Keycloak roles to Spring Security authorities.
     * Reads from:
     * - realm_access.roles
     * - resource_access.<client>.roles (all clients)
     *
     * Produces authorities like: ROLE_embedding-admin, ROLE_assistant-admin, ROLE_embedding-user
     */
    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> keycloakRolesConverter() {
        return jwt -> {
            Set<GrantedAuthority> mapped = new HashSet<>();

            // realm_access.roles
            Object realmAccessObj = jwt.getClaim("realm_access");
            if (realmAccessObj instanceof Map<?, ?> realmAccess) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof Collection<?> roles) {
                    for (Object r : roles) {
                        if (r instanceof String role) {
                            mapped.add(new SimpleGrantedAuthority("ROLE_" + role));
                        }
                    }
                }
            }

            // resource_access.<client>.roles
            Object resourceAccessObj = jwt.getClaim("resource_access");
            if (resourceAccessObj instanceof Map<?, ?> resourceAccess) {
                for (Object accessObj : resourceAccess.values()) {
                    if (!(accessObj instanceof Map<?, ?> access)) continue;

                    Object rolesObj = access.get("roles");
                    if (!(rolesObj instanceof Collection<?> roles)) continue;

                    for (Object r : roles) {
                        if (r instanceof String role) {
                            mapped.add(new SimpleGrantedAuthority("ROLE_" + role));
                        }
                    }
                }
            }

            return mapped;
        };
    }
}