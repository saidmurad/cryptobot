package com.binance.bot.onetimetasks;

import com.binance.bot.tradesignals.TimeFrame;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.MarginApi;
import io.gate.gateapi.models.MarginCurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PPOBackfill {
  private final MarginApi marginApi;
  private final MACDDataDao macdDataDao;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  PPOBackfill(GateIoClientFactory gateIoClientFactory, MACDDataDao macdDataDao) {
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

  private void backFill(List<MACDData> macdDataList) {
    // EMA26 is filled starting from 26th rrow
    // MACD starting 35th
    double ppoMacd_sum_first_period = 0.0;
    for (int i = 26; i < 35; i++) {
      macdDataList.get(i).ppoMacd =
          (macdDataList.get(i).ema12 - macdDataList.get(i).ema26) / macdDataList.get(i).ema26 * 100;
      ppoMacd_sum_first_period += macdDataList.get(i).ppoMacd;
    }
    macdDataList.get(34).ppoMacdSignalLine = ppoMacd_sum_first_period / 9;
    macdDataList.get(34).ppoHistogram = macdDataList.get(34).ppoMacd - macdDataList.get(34).ppoMacdSignalLine;
    double multiplier = 2.0 / (9 + 1);
    for (int i = 35; i < macdDataList.size(); i++) {
      if (macdDataList.get(i).ema26 == 0.0) {
        logger.info("Ignoring coin pair " + macdDataList.get(i).coinPair + " due to zero ema26");
        return;
      }
      macdDataList.get(i).ppoMacd =
          (macdDataList.get(i).ema12 - macdDataList.get(i).ema26) / macdDataList.get(i).ema26 * 100;
      macdDataList.get(i).ppoMacdSignalLine = (1 - multiplier) * macdDataList.get(i - 1).ppoMacdSignalLine
      + multiplier * macdDataList.get(i).ppoMacd;
      macdDataList.get(i).ppoHistogram = macdDataList.get(i).ppoMacd - macdDataList.get(i).ppoMacdSignalLine;
      macdDataDao.updatePPOMacd(macdDataList.get(i));
    }
  }
}
