package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmbeddingAdminController.class)
class EmbeddingAdminControllerTest {

    @TestConfiguration
    static class Mocks {
        @Bean
        FileEmbeddingRepository repo() {
            return Mockito.mock(FileEmbeddingRepository.class);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired FileEmbeddingRepository repo;

    @Test
    void patchMarksDeprecated() throws Exception {
        Mockito.when(repo.markDeprecatedByModule("demo", true)).thenReturn(3);
        mvc.perform(patch("/api/embeddings/module/demo")
                        .param("deprecated", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void deleteHard() throws Exception {
        mvc.perform(delete("/api/embeddings/module/demo"))
                .andExpect(status().isOk());
        Mockito.verify(repo).deleteByModule("demo");
    }
}