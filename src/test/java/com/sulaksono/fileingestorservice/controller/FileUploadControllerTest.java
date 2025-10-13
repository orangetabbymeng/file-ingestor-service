package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the controller accepts a file and returns HTTP 202.
 */
@WebMvcTest(FileUploadController.class)
@AutoConfigureMockMvc
class FileUploadControllerTest {

    @TestConfiguration
    static class Mocks {

        @Bean
        ProcessingService processingService() {
            return Mockito.mock(ProcessingService.class);
        }

        @Bean
        FileStorageService fileStorageService() {
            return Mockito.mock(FileStorageService.class);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ProcessingService processor;

    @Autowired
    FileStorageService storage;

    @Test
    void uploadReturnsAccepted() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "files", "example.md", MediaType.TEXT_PLAIN_VALUE, "Hello".getBytes());

        MockMultipartFile module = new MockMultipartFile(
                "module", "", MediaType.TEXT_PLAIN_VALUE, "order-service".getBytes());

        Path dummy = Path.of("/tmp/example.md");

        Mockito.when(storage.save(any())).thenReturn(dummy);
        Mockito.doNothing().when(processor).processAsync(dummy, "order-service");

        mvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .file(module))
                .andExpect(status().isAccepted());
    }
}