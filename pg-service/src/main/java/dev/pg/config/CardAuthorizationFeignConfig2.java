package dev.pg.config;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CardAuthorizationFeignConfig2 {

    @Bean
    public Request.Options cardAuthorizationRequestOptions2(
            @Value("${pg.client.card-authorization-2.connect-timeout-ms:1000}") long connectTimeoutMillis,
            @Value("${pg.client.card-authorization-2.read-timeout-ms:3000}") long readTimeoutMillis
    ) {
        return new Request.Options(
                connectTimeoutMillis,
                TimeUnit.MILLISECONDS,
                readTimeoutMillis,
                TimeUnit.MILLISECONDS,
                true
        );
    }
}
