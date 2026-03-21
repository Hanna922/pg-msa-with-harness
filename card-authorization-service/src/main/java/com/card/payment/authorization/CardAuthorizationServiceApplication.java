package com.card.payment.authorization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 카드 승인 서비스 애플리케이션
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
public class CardAuthorizationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardAuthorizationServiceApplication.class, args);
    }
}
