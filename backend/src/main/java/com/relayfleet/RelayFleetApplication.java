package com.relayfleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RelayFleetApplication {
    public static void main(String[] args) {
        SpringApplication.run(RelayFleetApplication.class, args);
    }
}