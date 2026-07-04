package com.sulaksono.fileingestorservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRolesConverterTest {

    private Converter<Jwt, Collection<GrantedAuthority>> converter;

    @BeforeEach
    void setup() {
        converter = new SecurityConfig().keycloakRolesConverter();
    }

    @Test
    void shouldMapRealmAccessRoles() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("embedding-admin", "embedding-user"))
        ));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(authorities(result))
                .containsExactlyInAnyOrder(
                        "ROLE_embedding-admin",
                        "ROLE_embedding-user"
                );
    }

    @Test
    void shouldMapResourceAccessRolesAcrossAllClients() {
        Jwt jwt = jwt(Map.of(
                "resource_access", Map.of(
                        "client-a", Map.of("roles", List.of("assistant-admin")),
                        "client-b", Map.of("roles", List.of("embedding-user"))
                )
        ));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(authorities(result))
                .containsExactlyInAnyOrder(
                        "ROLE_assistant-admin",
                        "ROLE_embedding-user"
                );
    }

    @Test
    void shouldMergeRealmAndResourceRoles() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("embedding-user")),
                "resource_access", Map.of(
                        "client-a", Map.of("roles", List.of("embedding-admin"))
                )
        ));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(authorities(result))
                .containsExactlyInAnyOrder(
                        "ROLE_embedding-user",
                        "ROLE_embedding-admin"
                );
    }

    @Test
    void shouldDeduplicateOverlappingRoles() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("embedding-admin")),
                "resource_access", Map.of(
                        "client-a", Map.of("roles", List.of("embedding-admin"))
                )
        ));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(authorities(result))
                .containsExactly("ROLE_embedding-admin");
    }

    @Test
    void shouldIgnoreNonStringRoles() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("embedding-admin", 42, true))
        ));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(authorities(result))
                .containsExactly("ROLE_embedding-admin");
    }

    @Test
    void shouldReturnEmptyWhenNoClaimsPresent() {
        Jwt jwt = jwt(Map.of());

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenClaimsAreOfWrongShape() {
        Jwt jwt = jwt(Map.of(
                "realm_access", "not-a-map",
                "resource_access", List.of("also-wrong")
        ));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipResourceAccessEntriesWithoutRoles() {
        Jwt jwt = jwt(Map.of(
                "resource_access", Map.of(
                        "client-a", Map.of("roles", List.of("embedding-user")),
                        "client-b", Map.of("no-roles-field", "value")
                )
        ));

        Collection<GrantedAuthority> result = converter.convert(jwt);

        assertThat(authorities(result))
                .containsExactly("ROLE_embedding-user");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(claims))
                .build();
    }

    private static List<String> authorities(Collection<GrantedAuthority> auths) {
        return auths.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}