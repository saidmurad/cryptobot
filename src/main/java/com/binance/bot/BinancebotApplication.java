package com.binance.bot;

import com.binance.bot.onetimetasks.ExecuteExitPositions;
import com.binance.bot.signalsuccessfailure.PriceTargetCheckerLaggingTask;
import com.binance.bot.testing.GetOrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.mail.MessagingException;
import java.text.ParseException;

@SpringBootApplication(scanBasePackages = {"com.binance.bot", "com.binance.api.client"})
@Configuration
@EnableScheduling
public class BinancebotApplication implements CommandLineRunner {

  @Autowired
	private ExecuteExitPositions executeExitPositions;
@Autowired private GetOrderStatus getOrderStatus;
	public static void main(String[] args) {
		SpringApplication.run(BinancebotApplication.class, args);
	}

	@Override
	public void run(String... args) throws ParseException, MessagingException {
		getOrderStatus.getOrderStatus();
		executeExitPositions.perform();
	}
}

