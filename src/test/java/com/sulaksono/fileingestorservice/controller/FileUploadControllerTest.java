package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileUploadController.class)
@AutoConfigureMockMvc
class FileUploadControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProcessingService processor;

    @MockBean
    private FileStorageService storage;

    @Test
    @WithMockUser(roles = "UPLOAD")
    void uploadReturnsAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "Example.java",
                MediaType.TEXT_PLAIN_VALUE,
                "class Example {}".getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile module = new MockMultipartFile(
                "module",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "order-service".getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile fileVersion = new MockMultipartFile(
                "fileVersion",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "1".getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile repoCloneUrl = new MockMultipartFile(
                "repoCloneUrl",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "https://example.com/repo.git".getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile repoRef = new MockMultipartFile(
                "repoRef",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "main".getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile pathInRepo = new MockMultipartFile(
                "pathInRepo",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "/src".getBytes(StandardCharsets.UTF_8)
        );

        Path stored = Path.of("/tmp/Example.java");
        when(storage.save(any(MultipartFile.class))).thenReturn(stored);

        mvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .file(module)
                        .file(fileVersion)
                        .file(repoCloneUrl)
                        .file(repoRef)
                        .file(pathInRepo)
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted[0]").value("Example.java"))
                .andExpect(jsonPath("$.rejected").isEmpty());

        verify(storage).save(any(MultipartFile.class));
        verify(processor).processAsync(
                eq(stored),
                eq("order-service"),
                eq("1"),
                eq("https://example.com/repo.git"),
                eq("main"),
                eq("/src")
        );
    }
}