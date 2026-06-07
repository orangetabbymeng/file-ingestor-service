package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.repository.CanonicalFileRepository;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import com.sulaksono.fileingestorservice.service.ReembeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmbeddingAdminController.class)
class EmbeddingAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FileEmbeddingRepository repo;

    @MockBean
    private CanonicalFileRepository cfRepo;

    @MockBean
    private ReembeddingService reembed;

    @Test
    @WithMockUser(roles = "DEPRECATE")
    void patchMarksDeprecated() throws Exception {
        when(repo.markDeprecatedByModule("demo", true)).thenReturn(3);

        mvc.perform(
                        patch("/api/embeddings/module/{module}", "demo")
                                .param("deprecated", "true")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().string("updated: 3"));

        verify(repo).markDeprecatedByModule("demo", true);
        verify(reembed).reembedModule("demo");
    }

    @Test
    @WithMockUser(roles = "DELETE")
    void deleteHard() throws Exception {
        mvc.perform(
                        delete("/api/embeddings/module/{module}", "demo")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(content().string("deleted all rows for module: demo"));

        verify(repo).deleteByModule("demo");
        verify(cfRepo).deleteByModule("demo");
        verifyNoInteractions(reembed);
    }
}