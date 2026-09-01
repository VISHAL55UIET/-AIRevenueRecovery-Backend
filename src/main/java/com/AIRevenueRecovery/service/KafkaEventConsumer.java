package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.config.KafkaConfig;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaEventConsumer {

    @KafkaListener(
            topics = KafkaConfig.RECOVERY_EVENTS_TOPIC,
            groupId = "ai-revenue-recovery"
    )
    public void consume(String message) {

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Kafka event received:"
        );

        System.out.println(
                message
        );

        System.out.println(
                "========================================"
        );
    }
}