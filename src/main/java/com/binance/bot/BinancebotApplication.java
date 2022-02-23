package com.binance.bot;

import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.bitcoinmonitoring.BitcoinMonitoringTask;
import com.binance.bot.onetimetasks.ExecuteExitPositions;
import com.binance.bot.onetimetasks.ProfitPercentageWithMoneyReuseCalculation;
import com.binance.bot.signalsuccessfailure.MarketPriceStream;
import com.binance.bot.testing.CancelOrders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.ParseException;

@SpringBootApplication(scanBasePackages = {"com.binance.bot", "com.binance.api.client"})
@Configuration
//@EnableScheduling
public class BinancebotApplication implements CommandLineRunner {

  @Autowired
	private ExecuteExitPositions executeExitPositions;
	//@Autowired private CancelOrders cancelOrders;
	@Autowired private MarketPriceStream marketPriceStream;
	@Autowired private ProfitPercentageWithMoneyReuseCalculation calculation;
	@Autowired private BitcoinMonitoringTask bitcoinMonitoringTask;

	public static void main(String[] args) {
		SpringApplication.run(BinancebotApplication.class, args);
	}

	@Override
	public void run(String... args) throws ParseException, MessagingException, IOException, BinanceApiException, InterruptedException {
		//calculation.calculate();
		// TODO: remove.marketPriceStream.addSymbol("BTCUSDT");
		//cancelOrders.cancelOrders();
		//TODO: remove.executeExitPositions.perform();
		bitcoinMonitoringTask.backFill();
	}
}

