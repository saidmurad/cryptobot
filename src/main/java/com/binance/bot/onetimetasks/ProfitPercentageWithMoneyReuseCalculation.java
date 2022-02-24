package com.binance.bot.onetimetasks;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.ChartPatternSignalMapper;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * at margin level 1.5
 * total val: 100
 * borrowed val: 66.67
 * net val: 33.33
 *
 * drop for erasure to 1.3 (margin call):
 * total val: 86.67
 * borrowed val: 66.67
 * net val: 20
 * % drop in total val (i.e drop in cmv) = 13.33
 *
 * drop for erasure to 1.1 (liquidation):
 * total val: 73.33
 * borrowed val: 66.67
 * net val: 6.67
 * % drop in total val = 26.67%
 */
@Component
public class ProfitPercentageWithMoneyReuseCalculation {
  private static final double STOP_LOSS_PERCENT = 20;
  private static final boolean USE_MARGIN = true;
  private static final double MARGIN_LEVEL_TO_USE = 1.5;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BinanceApiRestClient binanceApiRestClient;
  private final static double AMOUNT_PER_TRADE = 10;
  /*private static final String QUERY_USING_ALTFINS_INVALIDATION = "select * from ChartPatternSignal
  where ProfitPotentialPercent > 0" +
      "DateTime(TimeOfSignal)>=DateTime('2022-02-08 08:55') " +
      "order by TimeOfSignal";*/
  private static final String QUERY = "select * from ChartPatternSignal " +
      // Filtering for IsPriceTargetMet because the calculation might just not have caught it so why consider it for
      // stop loss alone but not for whether profit met, better filter it out.
      "where ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and TimeFrame != 'DAY'" +
      //"and DateTime(TimeOfSignal)>=DateTime('2022-02-20 00:00') " +
      "order by TimeOfSignal";
  private final Map<String, Boolean> symbolAndIsMarginTradingAllowed = new HashMap<>();
  private final boolean avoidOverdoneTradeTypes = false;
  private final ChartPatternSignalDaoImpl dao;
  @Autowired
  public ProfitPercentageWithMoneyReuseCalculation(BinanceApiClientFactory binanceApiClientFactory,
                                                   ChartPatternSignalDaoImpl dao) throws BinanceApiException {
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    binanceApiRestClient.getExchangeInfo().getSymbols().forEach(symbolInfo -> {
      symbolAndIsMarginTradingAllowed.put(symbolInfo.getSymbol(), symbolInfo.isMarginTradingAllowed());
    });
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  double invested = 0;
  double coffer = 0.0;
  double lockedInTrades = 0.0;
  double borrowed = 0.0;
  int numTradesLive = 0;
  Iterator<Map.Entry<Date, Pair<Integer, Double>>> eventDateIterator;
  Map.Entry<Date, Pair<Integer, Double>> nextEvent;

  private Map<Date, List<ChartPatternSignal>> patternsNotInTargetTimeProcessedSet =
      new TreeMap<>(new Comparator<Date>() {
    @Override
    public int compare(Date t1, Date t2) {
      return t1.compareTo(t2);
    }
  });

  private TradeType getOverdoneTradeType(Date time, TimeFrame timeFrame) {
    double threshold = 4;
    if (timeFrame == TimeFrame.FOUR_HOURS) {
      threshold = 6;
    }
    List<Pair<Double, Double>> results = jdbcTemplate.query(
        String.format("select CandleOpenPrice, CandleClosePrice from BitcoinPriceMonitoring where Time < '%s' and TimeFrame ='%s' " +
            "order by Time desc limit 10", df.format(time), timeFrame.name()), new Object[]{},
        (rs, num) -> {
          return Pair.of(rs.getDouble("CandleOpenPrice"), rs.getDouble("CandleClosePrice"));
        }
    );
    boolean isLastCandleGreen = (results.get(9).getSecond() - results.get(9).getFirst()) >= 0;
    double lastPrice = results.get(9).getSecond();
    for (int i = 9; i >= 0; i--) {
      boolean isGreen = (results.get(i).getSecond() - results.get(i).getFirst()) >= 0;
      if (isGreen != isLastCandleGreen) {
        return TradeType.NONE;
      }
      if (Math.abs((lastPrice - results.get(i).getFirst())/results.get(i).getFirst() * 100) >= threshold) {
        return isGreen ? TradeType.BUY : TradeType.SELL;
      }
    }
    return TradeType.NONE;
  }

  public void calculate() {
    List<ChartPatternSignal> chartPatternSignals = jdbcTemplate.query(QUERY, new ChartPatternSignalMapper());
    if (avoidOverdoneTradeTypes) {
      chartPatternSignals = filterOutOverdoneTradeTypesUseDynamic(chartPatternSignals);
    }
    if (USE_MARGIN) {
      List<ChartPatternSignal> chartPatternSignalsFiltered = chartPatternSignals.stream().filter(chartPatternSignal ->
          symbolAndIsMarginTradingAllowed.get(chartPatternSignal.coinPair())).collect(toList());
      List<ChartPatternSignal> chartPatternSignalsFilteredOut = chartPatternSignals.stream().filter(chartPatternSignal ->
          !symbolAndIsMarginTradingAllowed.get(chartPatternSignal.coinPair())).collect(toList());
      Set<String> filteredSymbols = new HashSet<>();
      chartPatternSignalsFilteredOut.forEach(chartPatternSignal -> {
        filteredSymbols.add(chartPatternSignal.coinPair());
      });
      logger.info("Filtered out " + filteredSymbols.size() + " symbols not supported for margin trading.");
      for (String symbol: filteredSymbols) {
        logger.info(symbol);
      }
      chartPatternSignals = chartPatternSignalsFiltered;
    }

    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate = getAmountsReleaseByDateCalendarUsingStopLossStrategyNotAltfinInvalidationTime(chartPatternSignals);

    eventDateIterator = amountsReleasedByDate.entrySet().iterator();
    nextEvent = eventDateIterator.next();

    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      processTradeExitEventsUntilGivenDate(chartPatternSignal.timeOfSignal());
      if (coffer >= AMOUNT_PER_TRADE) {
        coffer -= AMOUNT_PER_TRADE;
        lockedInTrades += AMOUNT_PER_TRADE;
        printWallet("Entering trade reusing from coffer", chartPatternSignal.timeOfSignal());
      } else if (USE_MARGIN && (coffer + lockedInTrades + AMOUNT_PER_TRADE) / (borrowed + AMOUNT_PER_TRADE) > MARGIN_LEVEL_TO_USE) {
        borrowed += AMOUNT_PER_TRADE;
        lockedInTrades += AMOUNT_PER_TRADE;
        printWallet("Entering trade with borrowed money", chartPatternSignal.timeOfSignal());
      } else {
        invested += AMOUNT_PER_TRADE;
        lockedInTrades += AMOUNT_PER_TRADE;
        printWallet("Entering trade with additonal investment", chartPatternSignal.timeOfSignal());
      }
      numTradesLive++;
      if (!patternsWithReleaseByDateProcessed.contains(chartPatternSignal)) {
        List<ChartPatternSignal> chartPatternList = patternsNotInTargetTimeProcessedSet.get(chartPatternSignal.timeOfSignal());
        if (chartPatternList == null) {
          chartPatternList = new ArrayList<>();
          patternsNotInTargetTimeProcessedSet.put(chartPatternSignal.timeOfSignal(), chartPatternList);
        }
        chartPatternList.add(chartPatternSignal);
      }
    }
    // null is passed to process event dates calendar to finish.`
    processTradeExitEventsUntilGivenDate(null);
    printWallet("The end", new Date());
    dumpStateOfLockedChartPatterns();
  }

  private List<ChartPatternSignal> filterOutOverdoneTradeTypesUsePrecalculated(List<ChartPatternSignal> chartPatternSignals) {
    List<ChartPatternSignal> filtered = chartPatternSignals.stream().filter(chartPatternSignal -> {
      try {
        TradeType overdoneTradeType = dao.getOverdoneTradeType(chartPatternSignal.timeOfSignal(),
            chartPatternSignal.timeFrame());
        return chartPatternSignal.tradeType() != overdoneTradeType;
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }).collect(toList());
    logger.info(String.format("Filtered out %d overdone chart patterns.", chartPatternSignals.size() - filtered.size()));
    return filtered;
  }

  private List<ChartPatternSignal> filterOutOverdoneTradeTypesUseDynamic(List<ChartPatternSignal> chartPatternSignals) {
    List<ChartPatternSignal> filtered = chartPatternSignals.stream().filter(chartPatternSignal -> {
      try {
        TradeType overdoneTradeType = getOverdoneTradeType(
            ChartPatternSignalDaoImpl.getCandlestickStart(chartPatternSignal.timeOfSignal(), chartPatternSignal.timeFrame()),
            chartPatternSignal.timeFrame());
        return chartPatternSignal.tradeType() != overdoneTradeType;
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }).collect(toList());
    logger.info(String.format("Filtered out %d overdone chart patterns.", chartPatternSignals.size() - filtered.size()));
    return filtered;
  }

  private void dumpStateOfLockedChartPatterns() {
    logger.info("\n*****************************");
    logger.info("Dumping the state of trades still locking money:");
    Iterator<Map.Entry<Date, List<ChartPatternSignal>>> itr = patternsNotInTargetTimeProcessedSet.entrySet().iterator();
    int total = 0;
    while (itr.hasNext()) {
      Map.Entry<Date, List<ChartPatternSignal>> entry = itr.next();
      logger.info("Date: " + entry.getKey() + " Number of trades locked on this date= " + entry.getValue().size());
      total += entry.getValue().size();
    }
    logger.info("Total number of locked trades=" + total);
    logger.info("Total nmber of trades expected="  + numTradesLive);
  }

  private void processTradeExitEventsUntilGivenDate(Date timeOfSignal) {
    boolean looped = false;
    while (nextEvent != null && (timeOfSignal == null || nextEvent.getKey().before(timeOfSignal)
        || nextEvent.getKey().equals(timeOfSignal))) {
      looped = true;
      coffer += nextEvent.getValue().getSecond();
      lockedInTrades -= nextEvent.getValue().getFirst() * AMOUNT_PER_TRADE;
      numTradesLive -= nextEvent.getValue().getFirst();
      logger.info(String.format("Releasing %f from %d trades for event date %s.",
          nextEvent.getValue().getSecond(), nextEvent.getValue().getFirst(), nextEvent.getKey()));
      if (eventDateIterator.hasNext()) {
        nextEvent = eventDateIterator.next();
      } else {
        nextEvent = null;
      }
    }
    if (looped) {
      printWallet("After processing events", timeOfSignal);
    }
  }

  private void printWallet(String context, Date time) {
    double totalAssetValue = coffer + lockedInTrades;
    double netAssetValue = totalAssetValue - borrowed;
    Double marginLevel = borrowed > 0? totalAssetValue / borrowed : null;
    double rr = (netAssetValue - invested) /invested * 100;
    logger.info(String.format("%s: On %s, invested=%f, coffer=%f, borrowed=%f, locked=%f, \ntotalAssetValue=%f, netAssetValue=%f, RR=%f, marginLevel=%s.",
        context, time, invested, coffer, borrowed, lockedInTrades, totalAssetValue, netAssetValue, rr,
        marginLevel != null ? marginLevel.toString() : "N/A"));
  }

  private Set<ChartPatternSignal> patternsWithReleaseByDateProcessed = new HashSet<>();

  private TreeMap<Date, Pair<Integer, Double>> getAmountsReleaseByDateCalendar(List<ChartPatternSignal> chartPatternSignals) {
    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate = new TreeMap<>();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      Date tradeExitTime;
      double amountReleasedFromTheTrade;
      if (chartPatternSignal.isPriceTargetMet() == null) {
        continue;
      }
      patternsWithReleaseByDateProcessed.add(chartPatternSignal);
      if (chartPatternSignal.isPriceTargetMet() &&
          (chartPatternSignal.timeOfSignalInvalidation().after(chartPatternSignal.priceTargetMetTime())
          || chartPatternSignal.timeOfSignalInvalidation().equals(chartPatternSignal.priceTargetMetTime()))) {
        tradeExitTime = chartPatternSignal.priceTargetMetTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPotentialPercent() / 100;
      } else {
        tradeExitTime = chartPatternSignal.timeOfSignalInvalidation();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPercentAtTimeOfSignalInvalidation() / 100;
      }
      Pair<Integer, Double> prevVal = amountsReleasedByDate.get(tradeExitTime);
      int tradeCount = 1;
      double amountReleased = amountReleasedFromTheTrade;
      if (prevVal != null) {
        tradeCount += prevVal.getFirst();
        amountReleased += prevVal.getSecond();
      }
      amountsReleasedByDate.put(tradeExitTime, Pair.of(tradeCount, amountReleased));
    }
    return amountsReleasedByDate;
  }

  private TreeMap<Date, Pair<Integer, Double>> getAmountsReleaseByDateCalendarUsingStopLossStrategyNotAltfinInvalidationTime(List<ChartPatternSignal> chartPatternSignals) {
    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate = new TreeMap<>();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      Date tradeExitTime;
      double amountReleasedFromTheTrade;
      patternsWithReleaseByDateProcessed.add(chartPatternSignal);
      if (chartPatternSignal.isPriceTargetMet() && chartPatternSignal.maxLossPercent() < STOP_LOSS_PERCENT) {
        tradeExitTime = chartPatternSignal.priceTargetMetTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPotentialPercent() / 100;
      } else if (!chartPatternSignal.isPriceTargetMet() && chartPatternSignal.maxLossPercent() < STOP_LOSS_PERCENT) {
        tradeExitTime = chartPatternSignal.timeOfSignalInvalidation();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPercentAtTimeOfSignalInvalidation() / 100;
      } else if (chartPatternSignal.maxLossPercent() >= STOP_LOSS_PERCENT) {
        tradeExitTime = chartPatternSignal.maxLossTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            -STOP_LOSS_PERCENT / 100;
      } else {
        // chartPatternSignal.isPriceTargetMet() == null
        continue;
      }
      Pair<Integer, Double> prevVal = amountsReleasedByDate.get(tradeExitTime);
      int tradeCount = 1;
      double amountReleased = amountReleasedFromTheTrade;
      if (prevVal != null) {
        tradeCount += prevVal.getFirst();
        amountReleased += prevVal.getSecond();
      }
      amountsReleasedByDate.put(tradeExitTime, Pair.of(tradeCount, amountReleased));
    }
    return amountsReleasedByDate;
}
}
