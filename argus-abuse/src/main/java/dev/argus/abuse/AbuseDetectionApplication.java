package dev.argus.abuse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Consumes request events from all instances and publishes block decisions. */
@SpringBootApplication
public class AbuseDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AbuseDetectionApplication.class, args);
    }
}
