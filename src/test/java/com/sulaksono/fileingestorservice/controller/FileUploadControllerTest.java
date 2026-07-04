package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileUploadController.class)
@Import({
        FileUploadControllerTest.TestSecurityConfig.class,
        FileUploadControllerTest.TestAccessDeniedControllerAdvice.class
})

class FileUploadControllerTest {

    private static final String UPLOAD_URL = "/api/files/upload";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProcessingService processor;

    @MockitoBean
    private FileStorageService storage;

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/files/upload")
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

    @RestControllerAdvice
    static class TestAccessDeniedControllerAdvice {

        @ExceptionHandler({
                AccessDeniedException.class,
                AuthorizationDeniedException.class
        })
        ResponseEntity<Void> handleAccessDenied(Exception ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private static MockMultipartFile part(String name, String value) {
        return new MockMultipartFile(
                name,
                "",
                MediaType.TEXT_PLAIN_VALUE,
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static MockMultipartFile javaFile(String filename, String content) {
        return new MockMultipartFile(
                "files",
                filename,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @WithMockUser(roles = "embedding-user")
    void uploadRegularFile_returnsAccepted_andCallsProcessor() throws Exception {
        MockMultipartFile file = javaFile("Example.java", "class Example {}");

        Path stored = Path.of("/tmp/Example.java");
        when(storage.save(any(MultipartFile.class))).thenReturn(stored);

        try (MockedStatic<FileTypeResolver> resolver = mockStatic(FileTypeResolver.class)) {
            resolver.when(() -> FileTypeResolver.resolve("Example.java"))
                    .thenReturn(FileType.JAVA);

            mvc.perform(multipart(UPLOAD_URL)
                            .file(file)
                            .file(part("module", "order-service"))
                            .file(part("fileVersion", "1.0.0"))
                            .file(part("repoCloneUrl", "https://example.com/repo.git"))
                            .file(part("repoRef", "main"))
                            .file(part("pathInRepo", "/src"))
                            .with(csrf()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.accepted[0]").value("Example.java"))
                    .andExpect(jsonPath("$.rejected").isEmpty());
        }

        verify(storage).save(any(MultipartFile.class));
        verify(processor).processAsync(
                eq(stored),
                eq("order-service"),
                eq("1.0.0"),
                eq("https://example.com/repo.git"),
                eq("main"),
                eq("/src")
        );
    }

    @Test
    @WithMockUser(roles = "embedding-admin")
    void uploadRegularFile_withoutVersion_isRejected() throws Exception {
        MockMultipartFile file = javaFile("Example.java", "class Example {}");

        mvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .file(part("module", "order-service"))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").isEmpty())
                .andExpect(jsonPath("$.rejected[0]").value("Example.java (missing fileVersion)"));

        verifyNoInteractions(storage, processor);
    }

    @Test
    @WithMockUser(roles = "embedding-user")
    void uploadEmptyFile_isRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "files",
                "empty.java",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mvc.perform(multipart(UPLOAD_URL)
                        .file(empty)
                        .file(part("fileVersion", "1.0.0"))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").isEmpty())
                .andExpect(jsonPath("$.rejected[0]").value("empty.java (empty)"));

        verify(storage, never()).save(any(MultipartFile.class));
        verifyNoInteractions(processor);
    }

    @Test
    @WithMockUser(roles = "embedding-user")
    void uploadUnsupportedType_isRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "notes.xyz",
                MediaType.TEXT_PLAIN_VALUE,
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        try (MockedStatic<FileTypeResolver> resolver = mockStatic(FileTypeResolver.class)) {
            resolver.when(() -> FileTypeResolver.resolve("notes.xyz"))
                    .thenReturn(FileType.UNKNOWN);

            mvc.perform(multipart(UPLOAD_URL)
                            .file(file)
                            .file(part("fileVersion", "1.0.0"))
                            .with(csrf()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.accepted").isEmpty())
                    .andExpect(jsonPath("$.rejected[0]").value("notes.xyz (unsupported type)"));
        }

        verifyNoInteractions(storage, processor);
    }

    @Test
    void upload_withoutAuthentication_isUnauthorized() throws Exception {
        MockMultipartFile file = javaFile("Example.java", "class Example {}");

        mvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .file(part("fileVersion", "1.0.0"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "wrong-role")
    void upload_withWrongRole_isForbidden() throws Exception {
        MockMultipartFile file = javaFile("Example.java", "class Example {}");

        mvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .file(part("fileVersion", "1.0.0"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}