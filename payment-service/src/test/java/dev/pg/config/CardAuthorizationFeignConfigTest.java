package dev.pg.config;

import feign.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardAuthorizationFeignConfigTest {

    private final CardAuthorizationFeignConfig config = new CardAuthorizationFeignConfig();

    @Test
    void shouldCreateRequestOptionsWithProvidedTimeouts() {
        Request.Options options = config.cardAuthorizationRequestOptions(1500, 4500);

        assertEquals(1500, options.connectTimeoutMillis());
        assertEquals(4500, options.readTimeoutMillis());
        assertEquals(true, options.isFollowRedirects());
    }
}
