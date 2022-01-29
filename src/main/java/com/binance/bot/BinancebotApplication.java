package com.binance.bot;

import com.binance.bot.altfins.AltfinPatternsReader;
import com.binance.bot.signalsuccessfailure.onetimetasks.SetTenCandlestickTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.binance.bot", "com.binance.api.client"})
@Configuration
@EnableScheduling
public class BinancebotApplication implements CommandLineRunner {
	@Autowired
	private AltfinPatternsReader altfinPatternsReader;
	@Autowired
	private SetTenCandlestickTime setTenCandlestickTime;

	public static void main(String[] args) {
		SpringApplication.run(BinancebotApplication.class, args);
	}

	@Override
	public void run(String... args) {
		new Thread(altfinPatternsReader).start();
		setTenCandlestickTime.perform();
	}
}

