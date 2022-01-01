package com.binance.bot;

import com.binance.bot.futures.BottomFisherFutures;
import com.binance.bot.futures.BreakoutPingPong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.binance.bot", "com.binance.api.client"})
public class BinancebotApplication implements CommandLineRunner {
	public static void main(String[] args) {
		SpringApplication.run(BinancebotApplication.class, args);
	}

	@Autowired
	private BottomFisherFutures bottomFisherFutures;

	@Autowired
	private BreakoutPingPong breakoutPingpong;

	@Override
	public void run(String... args) {

	}
}

