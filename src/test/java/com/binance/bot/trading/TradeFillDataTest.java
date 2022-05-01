package com.binance.bot.trading;

import com.binance.api.client.domain.account.MarginNewOrderResponse;
import com.binance.api.client.domain.account.Trade;
import com.binance.bot.tradesignals.TradeType;
import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static com.binance.bot.common.Util.decimalCompare;
import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class TradeFillDataTest {

  @Test
  public void buyFills() throws ParseException {
    MarginNewOrderResponse buyTradeResponse = new MarginNewOrderResponse();
    Trade fill1 = new Trade();
    fill1.setQty("12.2");
    fill1.setPrice("0.881");
    fill1.setCommission("0.0122");
    buyTradeResponse.setFills(ImmutableList.of(fill1, fill1));

    TradeFillData tradeFillData = new TradeFillData(buyTradeResponse, TradeType.BUY, 1.5);

    assertThat(decimalCompare(tradeFillData.getAvgPrice(), 0.881)).isTrue();
    assertThat(tradeFillData.getQuantity()).isEqualTo(24.3756);
    assertThat(tradeFillData.getCommissionUSDT()).isEqualTo(0.0366);
  }

  @Test
  public void sellFills() throws ParseException {
    MarginNewOrderResponse sellTradeResponse = new MarginNewOrderResponse();
    Trade fill1 = new Trade();
    fill1.setQty("12.2");
    fill1.setPrice("0.881");
    fill1.setCommission("0.0122");
    sellTradeResponse.setFills(ImmutableList.of(fill1, fill1));

    TradeFillData tradeFillData = new TradeFillData(sellTradeResponse, TradeType.SELL, 1.5);

    assertThat(decimalCompare(tradeFillData.getAvgPrice(), 0.881)).isTrue();
    assertThat(tradeFillData.getQuantity()).isEqualTo(24.4);
    assertThat(tradeFillData.getCommissionUSDT()).isEqualTo(0.0244);
  }
}