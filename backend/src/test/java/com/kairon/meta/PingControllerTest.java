package com.kairon.meta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: /api/v1/ping is public (SecurityConfig permits it); the slice
// only needs the controller, not the security chain.
@WebMvcTest(PingController.class)
@AutoConfigureMockMvc(addFilters = false)
class PingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingReturnsPongTrueAndAVersion() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pong").value(true))
                .andExpect(jsonPath("$.version").value("dev"));
    }
}
