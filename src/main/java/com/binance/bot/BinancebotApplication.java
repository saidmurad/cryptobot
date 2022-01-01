package com.binance.bot;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.binance.bot", "com.binance.api.client"})
public class BinancebotApplication implements CommandLineRunner {
	public static void main(String[] args) {
		SpringApplication.run(BinancebotApplication.class, args);
	}

	@Override
	public void run(String... args) {

	}
}

