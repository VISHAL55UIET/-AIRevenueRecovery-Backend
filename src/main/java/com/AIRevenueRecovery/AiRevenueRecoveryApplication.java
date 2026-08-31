package com.AIRevenueRecovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AiRevenueRecoveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(
                AiRevenueRecoveryApplication.class,
                args
        );
    }
}