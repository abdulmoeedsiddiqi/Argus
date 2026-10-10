package dev.argus.metering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Consumes request events, maintains the usage ledger and enforces monthly quotas. */
@SpringBootApplication
public class MeteringApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeteringApplication.class, args);
    }
}
