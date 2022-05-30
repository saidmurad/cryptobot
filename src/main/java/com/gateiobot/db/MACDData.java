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

  // 5 period EMA.
  public double histogramEMA;

  public HistogramTrendType histogramTrendType;

  public double ppoMacd;

  public double ppoMacdSignalLine;

  public double ppoHistogram;

  @Override
  public String toString() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return String.format("CoinPair: %s, TimeFrame: %s, Time: %s, CandleClosingPrice: %f, SMA: %f, TrendType: %s," +
        " SMASlope: %f, EMA12: %f, EMA26: %f, MACD: %f, MACD Signal: %f, Histogram: %f, HistogramEMA: %f," +
            "HistogramTrendType: %s.", coinPair, timeFrame.name(), dateFormat.format(time), candleClosingPrice, sma,
        trendType.name(), smaSlope, ema12, ema26, macd, macdSignal, histogram, histogramEMA, histogramTrendType.name());
  }
}
