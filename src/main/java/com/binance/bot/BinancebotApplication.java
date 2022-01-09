package com.binance.bot;

import com.binance.bot.altfins.AltfinPatternsReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.binance.bot", "com.binance.api.client"})
public class BinancebotApplication implements CommandLineRunner {
	@Autowired
	private AltfinPatternsReader altfinPatternsReader;

	public static void main(String[] args) {
		SpringApplication.run(BinancebotApplication.class, args);
	}

	@Override
	public void run(String... args) {
		new Thread(altfinPatternsReader).start();
	}
}

