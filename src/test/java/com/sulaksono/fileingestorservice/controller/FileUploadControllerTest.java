package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the controller accepts a file and returns HTTP 202.
 */
@WebMvcTest(FileUploadController.class)
@AutoConfigureMockMvc
class FileUploadControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ProcessingService processor;

    @MockBean
    FileStorageService storage;

    @Test
    void uploadReturnsAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "example.md", MediaType.TEXT_PLAIN_VALUE, "Hello".getBytes());

        Path dummy = Path.of("/tmp/example.md");
        org.mockito.Mockito.when(storage.save(ArgumentMatchers.any())).thenReturn(dummy);
        doNothing().when(processor).processAsync(dummy);

        mvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isAccepted());
    }
}