package com.binance.bot.onetimetasks;

import com.binance.bot.tradesignals.TimeFrame;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.HistogramTrendType;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import com.gateiobot.db.TrendType;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.MarginApi;
import io.gate.gateapi.models.MarginCurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MACDTrendBackfill {
  private final MarginApi marginApi;
  private final MACDDataDao macdDataDao;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  MACDTrendBackfill(GateIoClientFactory gateIoClientFactory, MACDDataDao macdDataDao) {
    this.marginApi = gateIoClientFactory.getMarginApi();
    this.macdDataDao = macdDataDao;
  }

  public void backFill() throws ApiException {
    List<MarginCurrencyPair> marginPairs = marginApi.listMarginCurrencyPairs();
    for (MarginCurrencyPair marginCurrencyPair: marginPairs) {
      for (TimeFrame timeFrame: TimeFrame.values()) {
        logger.info(String.format("Starting backfill for '%s' for timeframe '%s'.", marginCurrencyPair.getId(), timeFrame.name()));
        List<MACDData> macdDataList = macdDataDao.getFullMACDDataList(marginCurrencyPair.getId(), timeFrame);
        if (!macdDataList.isEmpty()) {
          backFill(macdDataList);
        }
      }
    }
  }

  private static final int EMA_N = 5;
  private void backFill(List<MACDData> macdDataList) {
    int nonZeroStart = 0;
    while (nonZeroStart < macdDataList.size() && macdDataList.get(nonZeroStart).histogram == 0.0 && nonZeroStart < macdDataList.size()) {
      nonZeroStart++;
    }
    if (nonZeroStart == macdDataList.size()) {
      logger.warn(String.format("All zero values for historgram for %s. Not backbfilling trend of histogram.", macdDataList.get(0).coinPair));
      return;
    }
    double sumN = 0.0;
    for (int i = nonZeroStart; i < nonZeroStart + EMA_N; i ++) {
      sumN += macdDataList.get(i).histogram;
    }
    macdDataList.get(EMA_N + nonZeroStart - 1).histogramEMA = sumN / EMA_N;
    double multiplier = 2.0 / (EMA_N + 1);
    for (int i = EMA_N + nonZeroStart; i < macdDataList.size(); i++) {
      macdDataList.get(i).histogramEMA = (1 - multiplier) * macdDataList.get(i - 1).histogramEMA + multiplier * macdDataList.get(i).histogram;
      if (macdDataList.get(i).histogram == macdDataList.get(i).histogramEMA) {
        macdDataList.get(i).histogramTrendType = HistogramTrendType.PLATEAUED;
      } else if (macdDataList.get(i).histogram < macdDataList.get(i).histogramEMA) {
        macdDataList.get(i).histogramTrendType = HistogramTrendType.DECELERATING;
      } else {
        macdDataList.get(i).histogramTrendType = HistogramTrendType.ACCELERATING;
      }
      macdDataDao.updateHistogramEMA(macdDataList.get(i));
    }
  }
}
