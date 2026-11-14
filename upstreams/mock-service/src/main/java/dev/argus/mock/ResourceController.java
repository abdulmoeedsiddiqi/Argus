package dev.argus.mock;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample resources of the demo backend. The same application runs as Users, Orders and
 * Products; SERVICE_NAME only labels the copy, so the gateway's choice of copy is visible.
 * Every answer first applies the current fault mode (see {@link FaultController}).
 */
@RestController
public class ResourceController {

    static final String SERVICE_HEADER = "X-Mock-Service";

    private final String serviceName;
    private final FaultState faultState;

    public ResourceController(@Value("${mock.service-name}") String serviceName,
                              FaultState faultState) {
        this.serviceName = serviceName;
        this.faultState = faultState;
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Object> order(@PathVariable long id) {
        return answer(new Order(id, "Pizza", "DELIVERED"));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Object> user(@PathVariable long id) {
        return answer(new User(id, "Sample User " + id, "user" + id + "@example.com"));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Object> product(@PathVariable long id) {
        return answer(new Product(id, "Sample Product " + id, 9.99));
    }

    private ResponseEntity<Object> answer(Object body) {
        FaultMode mode = faultState.current();
        if (mode == FaultMode.FAIL) {
            return ResponseEntity.internalServerError()
                    .header(SERVICE_HEADER, serviceName)
                    .body(Map.of("error", "injected failure"));
        }
        if (mode == FaultMode.SLOW) {
            pause();
        }
        return ResponseEntity.ok().header(SERVICE_HEADER, serviceName).body(body);
    }

    private void pause() {
        try {
            Thread.sleep(faultState.slowDelay());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    record Order(long id, String item, String status) { }

    record User(long id, String name, String email) { }

    record Product(long id, String name, double price) { }
}
