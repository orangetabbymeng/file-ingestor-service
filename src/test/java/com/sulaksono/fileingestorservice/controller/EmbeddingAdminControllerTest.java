package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.repository.CanonicalFileRepository;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import com.sulaksono.fileingestorservice.service.ReembeddingService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmbeddingAdminController.class)
@Import(EmbeddingAdminControllerTest.TestSecurityConfig.class)
class EmbeddingAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FileEmbeddingRepository repo;

    @MockitoBean
    private CanonicalFileRepository cfRepo;

    @MockitoBean
    private ReembeddingService reembed;

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.PATCH, "/api/embeddings/**")
                            .hasAnyRole("embedding-user", "embedding-admin", "assistant-admin")
                            .requestMatchers(HttpMethod.DELETE, "/api/embeddings/**")
                            .hasAnyRole("embedding-user", "embedding-admin", "assistant-admin")
                            .anyRequest().permitAll()
                    )
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                            )
                            .accessDeniedHandler(
                                    (request, response, accessDeniedException) ->
                                            response.sendError(HttpServletResponse.SC_FORBIDDEN)
                            )
                    )
                    .build();
        }
    }

    @Test
    @WithMockUser(roles = "embedding-user")
    void patchMarksDeprecated() throws Exception {

        when(repo.markDeprecatedByModule("demo", true))
                .thenReturn(3);

        mvc.perform(
                        patch("/api/embeddings/module/demo")
                                .param("deprecated", "true")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().string("updated: 3"));

        verify(repo).markDeprecatedByModule("demo", true);
        verify(reembed).reembedModule("demo");
    }

    @Test
    @WithMockUser(roles = "embedding-admin")
    void patchRestoresModule() throws Exception {

        when(repo.markDeprecatedByModule("demo", false))
                .thenReturn(2);

        mvc.perform(
                        patch("/api/embeddings/module/demo")
                                .param("deprecated", "false")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().string("updated: 2"));

        verify(repo).markDeprecatedByModule("demo", false);
        verify(reembed).reembedModule("demo");
    }

    @Test
    @WithMockUser(roles = "assistant-admin")
    void deleteHard() throws Exception {

        mvc.perform(
                        delete("/api/embeddings/module/demo")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "deleted all rows for module: demo"));

        verify(repo).deleteByModule("demo");
        verify(cfRepo).deleteByModule("demo");
        verifyNoInteractions(reembed);
    }

    @Test
    void patchWithoutAuthenticationReturns401() throws Exception {

        mvc.perform(
                        patch("/api/embeddings/module/demo")
                                .param("deprecated", "true")
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteWithoutAuthenticationReturns401() throws Exception {

        mvc.perform(
                        delete("/api/embeddings/module/demo")
                                .with(csrf())
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "wrong-role")
    void patchWithWrongRoleReturns403() throws Exception {

        mvc.perform(
                        patch("/api/embeddings/module/demo")
                                .param("deprecated", "true")
                                .with(csrf())
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(repo, cfRepo, reembed);
    }

    @Test
    @WithMockUser(roles = "wrong-role")
    void deleteWithWrongRoleReturns403() throws Exception {

        mvc.perform(
                        delete("/api/embeddings/module/demo")
                                .with(csrf())
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(repo, cfRepo, reembed);
    }

    @Test
    @WithMockUser(roles = "embedding-admin")
    void patchRepositoryFailureReturns5xx() throws Exception {

        when(repo.markDeprecatedByModule("demo", true))
                .thenThrow(new RuntimeException("boom"));

        mvc.perform(
                        patch("/api/embeddings/module/demo")
                                .param("deprecated", "true")
                                .with(csrf())
                )
                .andExpect(status().is5xxServerError());

        verify(reembed, never()).reembedModule(any());
    }
}