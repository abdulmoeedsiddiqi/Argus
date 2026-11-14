package dev.argus.mock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample resources of the demo backend. The same application runs as Users, Orders and
 * Products; SERVICE_NAME only labels the copy, so the gateway's choice of copy is visible.
 */
@RestController
public class ResourceController {

    static final String SERVICE_HEADER = "X-Mock-Service";

    private final String serviceName;

    public ResourceController(@Value("${mock.service-name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> order(@PathVariable long id) {
        return answer(new Order(id, "Pizza", "DELIVERED"));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> user(@PathVariable long id) {
        return answer(new User(id, "Sample User " + id, "user" + id + "@example.com"));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> product(@PathVariable long id) {
        return answer(new Product(id, "Sample Product " + id, 9.99));
    }

    private <T> ResponseEntity<T> answer(T body) {
        return ResponseEntity.ok().header(SERVICE_HEADER, serviceName).body(body);
    }

    record Order(long id, String item, String status) { }

    record User(long id, String name, String email) { }

    record Product(long id, String name, double price) { }
}
