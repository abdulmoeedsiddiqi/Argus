package dev.argus.mock;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fault injection for tests and demos: POST /fault?mode=ok|slow|fail. */
@RestController
public class FaultController {

    private final FaultState faultState;

    public FaultController(FaultState faultState) {
        this.faultState = faultState;
    }

    @PostMapping("/fault")
    public ResponseEntity<Map<String, String>> fault(@RequestParam String mode) {
        return FaultMode.parse(mode)
                .map(next -> {
                    faultState.switchTo(next);
                    return ResponseEntity.ok(Map.of("mode", next.label()));
                })
                .orElseGet(() -> ResponseEntity.badRequest()
                        .body(Map.of("error", "mode must be ok, slow or fail")));
    }
}
