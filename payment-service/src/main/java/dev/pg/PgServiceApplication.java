package dev.pg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PgServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PgServiceApplication.class, args);
	}

}
