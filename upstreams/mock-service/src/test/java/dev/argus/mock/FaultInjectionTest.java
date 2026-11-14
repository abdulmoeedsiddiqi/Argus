package dev.argus.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /fault makes this copy slow or failing on demand (scope document, Section 7.1), so that
 * health checks, timeouts, retries and circuit breakers can be tested later.
 */
@SpringBootTest(properties = "mock.slow-delay=200ms")
@AutoConfigureMockMvc
class FaultInjectionTest {

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void backToNormal() throws Exception {
        mvc.perform(post("/fault").param("mode", "ok"));
    }

    @Test
    @DisplayName("mode=fail makes resource requests answer 500")
    void failModeReturns500() throws Exception {
        mvc.perform(post("/fault").param("mode", "fail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("fail"));

        mvc.perform(get("/orders/42")).andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("mode=ok restores normal answers after a fault")
    void okModeRestoresNormalAnswers() throws Exception {
        mvc.perform(post("/fault").param("mode", "fail"));
        mvc.perform(post("/fault").param("mode", "ok")).andExpect(status().isOk());

        mvc.perform(get("/orders/42")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("mode=slow delays the answer by the configured delay, then answers normally")
    void slowModeDelaysTheAnswer() throws Exception {
        mvc.perform(post("/fault").param("mode", "slow")).andExpect(status().isOk());

        long start = System.nanoTime();
        mvc.perform(get("/orders/42")).andExpect(status().isOk());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("an unknown mode is rejected with 400 and the current mode is kept")
    void unknownModeIsRejected() throws Exception {
        mvc.perform(post("/fault").param("mode", "banana")).andExpect(status().isBadRequest());

        mvc.perform(get("/orders/42")).andExpect(status().isOk());
    }
}
