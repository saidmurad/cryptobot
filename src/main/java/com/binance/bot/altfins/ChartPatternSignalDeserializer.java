package com.binance.bot.altfins;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartPatternSignalDeserializer implements JsonDeserializer<List<ChartPatternSignal>> {
  private static final Map<String, TimeFrame> timeFrameMap = new HashMap<>();
  private static final Map<String, TradeType> tradeTypeMap = new HashMap<>();
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public ChartPatternSignalDeserializer() {
    tradeTypeMap.put("Buy", TradeType.BUY);
    tradeTypeMap.put("Sell", TradeType.SELL);

    timeFrameMap.put("1", TimeFrame.FIFTEEN_MINUTES);
    timeFrameMap.put("2", TimeFrame.HOUR);
    timeFrameMap.put("3", TimeFrame.FOUR_HOURS);
    timeFrameMap.put("4", TimeFrame.DAY);
  }

  @Override
  public List<ChartPatternSignal> deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
    List<ChartPatternSignal> chartPatternSignals = new ArrayList<>();
    JsonArray jsonArray = jsonElement.getAsJsonArray();
    for (JsonElement arrayElement: jsonArray) {
      try {
        chartPatternSignals.add(performMapping((JsonObject) arrayElement));
      } catch (ParseException e) {
        logger.error("Error while deserializing patterns.", e);
        throw new RuntimeException(e);
      }
    }
    return chartPatternSignals;
  }

  private ChartPatternSignal performMapping(JsonObject patternElement) throws ParseException {
    TradeType tradeType = tradeTypeMap.get(patternElement.get("trade_type").getAsString());
    ChartPatternSignal.Builder builder = ChartPatternSignal.newBuilder()
        .setCoinPair(patternElement.get("coin_pair").getAsString())
        .setTimeFrame(timeFrameMap.get(patternElement.get("interval").getAsString()))
        .setPattern(patternElement.get("pattern").getAsString())
        .setTradeType(tradeType)
        .setPriceAtTimeOfSignal(patternElement.get("current_price").getAsDouble())
        .setTimeOfSignal(dateFormat.parse(patternElement.get("signal_occurence_time").getAsString()))
        .setPattern(patternElement.get("pattern").getAsString())
        .setPriceTarget(getPriceTarget(patternElement.get("profit_target").getAsString(), tradeType))
        .setPriceTargetTime(dateFormat.parse(patternElement.get("profit_target_time").getAsString()));
    if (patternElement.get("profit_potential_percent").getAsString().length() > 0) {
      builder.setProfitPotentialPercent(patternElement.get("profit_potential_percent").getAsDouble());
    }
    return builder.build();
  }

  private double getPriceTarget(String priceTargetStr, TradeType tradeType) {
    if (priceTargetStr.contains("to")) {
      int ind = priceTargetStr.indexOf(' ');
      double fromPrice = Double.parseDouble(priceTargetStr.substring(0, ind));
      double toPrice =  Double.parseDouble(priceTargetStr.substring(ind + 4));
      switch (tradeType) {
        case BUY:
          return fromPrice;
        case SELL:
        default:
          if (fromPrice > toPrice) {
            return fromPrice;
          }
          return toPrice;
      }
    }
    return Double.parseDouble(priceTargetStr);
  }
}