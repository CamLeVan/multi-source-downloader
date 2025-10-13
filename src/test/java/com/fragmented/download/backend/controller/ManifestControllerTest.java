package com.fragmented.download.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManifestController.class)
public class ManifestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void getManifest_shouldReturnCorrectData() throws Exception {
        // Perform GET request to the endpoint
        mockMvc.perform(get("/manifest/testfile"))
                // 1. Verify HTTP Status is 200 OK
                .andExpect(status().isOk())
                // 2. Verify pieceSize value
                .andExpect(jsonPath("$.pieceSize").value(1048576))
                // 3. Verify the size of the pieces list
                .andExpect(jsonPath("$.pieces", hasSize(20000)))
                // 4. Verify the id of the first piece
                .andExpect(jsonPath("$.pieces[0].id").value(0))
                // 5. Verify that the sources of the first piece contain "origin"
                .andExpect(jsonPath("$.pieces[0].sources[0]").value("origin"));
    }
}
