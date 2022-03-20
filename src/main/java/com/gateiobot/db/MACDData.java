package com.gateiobot.db;

import com.binance.bot.tradesignals.TimeFrame;
import com.google.auto.value.AutoValue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class MACDData {
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  public String coinPair;
  public TimeFrame timeFrame;
  public Date time;
  public double candleClosingPrice;
  
  public double sma;

  public TrendType trendType;

  public double smaSlope;

  public double ema26;

  public double ema12;

  public double macd;

  public double macdSignal;

  public double histogram;

  public MomentumStatus momentumStatus;

  @Override
  public String toString() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return String.format("CoinPair: %s, TimeFrame: %s, Time: %s, CandleClosingPrice: %f, SMA: %f, TrendType: %s," +
        " SMASlope: %f.", coinPair, timeFrame.name(), dateFormat.format(time), candleClosingPrice, sma,
        trendType.name(), smaSlope);
  }
}
