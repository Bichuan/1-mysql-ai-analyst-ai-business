package com.aianalyst.config;

import com.aianalyst.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in test for the public health endpoint and protected actuator endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "runActuatorIT", matches = "true")
class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeHealthButProtectMetricsAndReturnRequestId() throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.REQUEST_ID_HEADER, "trace-20260714-1001"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "trace-20260714-1001"));

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
