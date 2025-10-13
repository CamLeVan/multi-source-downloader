package com.fragmented.download.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class ManifestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testManifestEndpoint_ReturnsCorrectStructureAndData() throws Exception {
        mockMvc.perform(get("/manifest/testfile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pieceSize").value(1048576))
                .andExpect(jsonPath("$.pieces", hasSize(20000)));
    }

    @Test
    void testSingletonBeanPerformance_forManifestEndpoint() throws Exception {
        int requestCount = 100;
        List<Long> responseTimes = new ArrayList<>();

        // First request to initialize the bean
        mockMvc.perform(get("/manifest/testfile")).andExpect(status().isOk());

        for (int i = 0; i < requestCount; i++) {
            Instant start = Instant.now();
            mockMvc.perform(get("/manifest/testfile"))
                    .andExpect(status().isOk());
            Instant end = Instant.now();
            responseTimes.add(Duration.between(start, end).toMillis());
        }

        System.out.println("Response times for " + requestCount + " requests: " + responseTimes);

        long averageResponseTime = (long) responseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        System.out.println("Average response time: " + averageResponseTime + "ms");

        // Assert that the average response time is very low (e.g., under 50ms)
        // This indicates that the manifest is not being regenerated each time.
        assert(averageResponseTime < 50);
    }
}
