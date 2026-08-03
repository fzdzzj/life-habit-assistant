package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.config.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RequestIdAndHealthHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointShouldBePublicAndReportUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void suppliedRequestIdShouldBeEchoedAndLogged(CapturedOutput output) throws Exception {
        String requestId = "trace-" + UUID.randomUUID();

        mockMvc.perform(post("/api/auth/register")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("id-" + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, requestId));

        assertThat(output.getAll()).contains(requestId);
        assertThat(output.getAll()).contains("request start POST /api/auth/register");
    }

    @Test
    void missingRequestIdShouldBeGenerated() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void requestIdShouldNotLeakAcrossRequests() throws Exception {
        String firstId = "leak-" + UUID.randomUUID();
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.REQUEST_ID_HEADER, firstId))
                .andExpect(status().isOk());

        MvcResult second = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(second.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo(firstId)
                .isNotBlank();
    }

    private String credentials(String username) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", "test-password"));
    }
}
