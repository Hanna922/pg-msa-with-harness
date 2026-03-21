package dev.pg.approval.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class PgTransactionIdGenerator {

    private static final DateTimeFormatter PG_TX_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String generate() {
        String timestamp = LocalDateTime.now().format(PG_TX_TIMESTAMP);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PG" + timestamp + suffix;
    }
}
