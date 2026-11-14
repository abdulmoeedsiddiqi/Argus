package dev.argus.mock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The mock backend answers with sample JSON, like a real Orders, Users or Products service would.
 * It is a supporting component (scope document, Section 7.1), so these tests carry no FR id.
 */
@SpringBootTest(properties = "mock.service-name=orders")
@AutoConfigureMockMvc
class ResourceControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET /orders/{id} returns the sample order as JSON")
    void returnsSampleOrder() throws Exception {
        mvc.perform(get("/orders/42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.item").value("Pizza"))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("GET /users/{id} returns a sample user as JSON")
    void returnsSampleUser() throws Exception {
        mvc.perform(get("/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    @DisplayName("GET /products/{id} returns a sample product as JSON")
    void returnsSampleProduct() throws Exception {
        mvc.perform(get("/products/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.price").isNumber());
    }

    @Test
    @DisplayName("every answer names the service copy (SERVICE_NAME) in the X-Mock-Service header")
    void namesTheServiceCopy() throws Exception {
        mvc.perform(get("/orders/1"))
                .andExpect(header().string("X-Mock-Service", "orders"));
    }
}
