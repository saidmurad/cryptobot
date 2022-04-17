package com.binance.bot.integration.gateio;

import com.binance.bot.BinancebotApplication;
import com.gateiobot.GateIoClientFactory;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.GateApiException;
import io.gate.gateapi.api.MarginApi;
import io.gate.gateapi.api.SpotApi;
import io.gate.gateapi.models.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@TestPropertySource(locations = {
    "classpath:application.properties"})
@Configuration
@ComponentScan(basePackages= "com.gateiobot")
public class MarginOrderTest {
  @Autowired
  GateIoClientFactory gateIoClientFactory;
  MarginApi marginApi;
  SpotApi spotApi;

  @Before
  public void setUp() {
    marginApi = gateIoClientFactory.getMarginApi();
    spotApi = gateIoClientFactory.getSpotApi();
  }

  @Test
  public void usdtBorrowedTrade() throws ApiException {
    /*MarginCurrencyPair beNull;
    try {
      beNull = marginApi.getMarginCurrencyPair("WTF_USDT");
    } catch (GateApiException ex) {
      System.err.println(ex.getErrorLabel());
    }
    CrossMarginAccount crossMarginAccount = marginApi.getCrossMarginAccount();
    // Starting state
    System.out.println(String.format(
        "Cross margin account total: %s, borrowed: %s, risk level: %s.",
        crossMarginAccount.getTotal(), crossMarginAccount.getBorrowed(), crossMarginAccount.getRisk()));
    Ticker ticker = spotApi.listTickers().currencyPair("BTC_USDT").execute().get(0);
    System.out.println(String.format("Ask price=%s.", ticker.getLowestAsk()));
    CurrencyPair spotCurrencyPair = spotApi.getCurrencyPair("BTC_USDT");

    String qty = getFormattedQuantity(6 / Double.parseDouble(ticker.getLowestAsk()), spotCurrencyPair.getAmountPrecision());
    Order order = new Order();
    order.setAccount(Order.AccountEnum.CROSS_MARGIN);
    order.setCurrencyPair("BTC_USDT");
    order.setType(Order.TypeEnum.LIMIT);
    order.setSide(Order.SideEnum.BUY);
    order.setAmount(qty);
    order.setPrice(ticker.getLowestAsk());
    order.setTimeInForce(Order.TimeInForceEnum.GTC);
    order.setAutoBorrow(true);
    Order orderResponse = null;
    try {
      orderResponse = spotApi.createOrder(order);
    } catch (GateApiException ex) {
      System.err.println(ex.getCause());
    }
    System.out.println(String.format("Placed limit order id %s with status %s.",
        orderResponse.getId(), orderResponse.getStatus().name()));
    Order.StatusEnum orderStatus = orderResponse.getStatus();
    boolean firstItr = true;
    do {
      if (firstItr) {
        printCrossMarginBalance();
        firstItr = false;
      }
      Order orderResp = spotApi.getOrder(orderResponse.getId(), "BTC_USDT", "cross_margin");
      orderStatus = orderResp.getStatus();
    } while (orderStatus != Order.StatusEnum.CLOSED);
    printCrossMarginBalance();

    List<CrossMarginLoan> loans = marginApi.listCrossMarginLoans(2).execute();
    for (CrossMarginLoan loan: loans) {
      System.out.println(String.format("Loan id=%s currency=%s amount=%s unrepaid interest=%s.",
          loan.getId(), loan.getCurrency(), loan.getAmount(), loan.getUnpaidInterest()));
    }

    // Sell
    ticker = spotApi.listTickers().currencyPair("BTC_USDT").execute().get(0);
    order = new Order();
    order.setAccount(Order.AccountEnum.CROSS_MARGIN);
    order.setCurrencyPair("BTC_USDT");
    order.setType(Order.TypeEnum.LIMIT);
    order.setSide(Order.SideEnum.SELL);
    order.setAmount(qty);
    order.setPrice(ticker.getLowestAsk());
    order.setTimeInForce(Order.TimeInForceEnum.GTC);
    orderResponse = spotApi.createOrder(order);
    System.out.println(String.format("Placed sel limit order id %s with status %s.",
        orderResponse.getId(), orderResponse.getStatus().name()));

     firstItr = true;
    do {
      if (firstItr) {
        printCrossMarginBalance();
        firstItr = false;
      }
      Order orderResp = spotApi.getOrder(orderResponse.getId(), "BTC_USDT", "cross_margin");
      orderStatus = orderResp.getStatus();
    } while (orderStatus != Order.StatusEnum.CLOSED);
    printCrossMarginBalance();

    loans = marginApi.listCrossMarginLoans(2).execute();*/
  }

  private void printCrossMarginBalance() throws ApiException {
    CrossMarginAccount crossMarginAccount = marginApi.getCrossMarginAccount();
    System.out.println(String.format(
        "Cross margin account total: %s, borrowed: %s, risk level: %s.",
        crossMarginAccount.getTotal(), crossMarginAccount.getBorrowed(), crossMarginAccount.getRisk()));
    CrossMarginBalance usdtBalance = crossMarginAccount.getBalances().get("USDT");
    System.out.println(String.format(
        "USDT total: %s, loced: %s, borrowed: %s, risk level: %s.",
        usdtBalance.getAvailable(), usdtBalance.getFreeze(), usdtBalance.getBorrowed(), crossMarginAccount.getRisk()));
  }

  String getFormattedQuantity(double qty, int stepSizeNumDecimalPlaces) {
    String pattern = "#";
    for (int i = 0; i < stepSizeNumDecimalPlaces; i ++) {
      if (i == 0) {
        pattern += ".";
      }
      pattern += "#";
    }
    DecimalFormat df = new DecimalFormat(pattern);
    df.setRoundingMode(RoundingMode.CEILING);
    return df.format(qty);
  }
}
