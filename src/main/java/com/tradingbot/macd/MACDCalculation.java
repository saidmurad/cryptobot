package com.tradingbot.macd;

import com.gateiobot.db.MACDData;

import java.util.List;

public class MACDCalculation {

  public boolean isMACDDataAvailable(List<MACDData> macdDataList) {
    MACDData lastData = macdDataList.get(macdDataList.size() -1);
    return !(lastData.macd == 0.0 && lastData.macdSignal == 0.0);
  }

}
