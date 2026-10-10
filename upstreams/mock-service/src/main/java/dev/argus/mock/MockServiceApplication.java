package dev.argus.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Demo upstream. One codebase, run as Users, Orders and Products, with a /chaos endpoint. */
@SpringBootApplication
public class MockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockServiceApplication.class, args);
    }
}
