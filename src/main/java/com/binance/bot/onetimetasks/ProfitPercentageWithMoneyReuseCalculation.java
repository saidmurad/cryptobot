package com.binance.bot.onetimetasks;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.CrossMarginPair;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.ChartPatternSignalMapper;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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

/**
 * Next things to do:
 * 1. priceattimeofsignalreal
 * 2. consider only prices at great distance from zero line
 * 3. RSI above 50 for bullish and vice-versa.
 */
@Component
public class ProfitPercentageWithMoneyReuseCalculation {
  private static final double STOP_LOSS_PERCENT = 10;
  private static final boolean USE_MARGIN = true;
  private static final double MARGIN_LEVEL_TO_USE = 1.5;
  private static final boolean USE_SIGNAL_INVALIDATIONS = false;
  private final boolean useMACDForEntrySameSignAsTradeType = true;
  private boolean usePrebreakoutCandlestickPriceForStopLoss = true;
  // Uses MACD line above or below signal line to determine entry to long or short respectively.
  private final boolean useHistogramForEntry = false;
  // Uses MACD signal line crossover to exit trade. No other conditions are used other than this.
  private final boolean useMACDForExit = false;
  private final boolean useHistogramTrendReversalForExit = false;
  private final boolean avoidOverdoneTradeTypes = false;
  private final BinanceApiMarginRestClient binanceApiMarginRestClient;

  @Autowired
  private JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BinanceApiRestClient binanceApiRestClient;
  private final static double AMOUNT_PER_TRADE = 10;
  private static final String QUERY_USING_INVALIDATIONS = "select * from ChartPatternSignal cps" +
      "where cps.ProfitPotentialPercent > 0 and cps.Attempt = 1 and " +
      "DateTime(cps.TimeOfSignal)>=DateTime('2022-02-08 08:55') " +
      " and cps.TimeFrame='FIFTEEN_MINUTES' and " +
      "((cps.TradeType='BUY' and macd.Trend ='BULLISH' and cps.PriceAtTimeOfSignal > macd.SMA) or (cps.Tradetype='SELL' and macd.Trend='BEARISH' and cps.PriceAtTimeOfSignal < macd.SMA)) " +
      //"and IsVolumeSurge=1 " +
      "order by cps.TimeOfSignal";
  private static final String QUERY_USING_STOPLOSSES = "select * from ChartPatternSignal " +
      // Filtering for IsPriceTargetMet because the calculation might just not have caught it so why consider it for
      // stop loss alone but not for whether profit met, better filter it out.
      "where ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and " +
      //"DateTime(TimeOfSignal)>DateTime('2022-02-28 00:00') and " +
      // "DateTime(TimeOfSignal)<=DateTime('2022-03-08 00:00') and " +
      "ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and " +
      //"TradeType='SELL' and " +
      //"(((TimeFrame = 'HOUR' or TimeFrame='FOUR_HOURS') and IsVolumeSurge=1) or TimeFrame == 'FIFTEEN_MINUTES' ) " +
      "TimeFrame == 'FIFTEEN_MINUTES' " +
      "order by TimeOfSignal";
  // TODO:    remove pice already jumped cases from query.
  private static final String QUERY_USING_MACD_SAME_SIGN_AS_TRADE_TYPE = "Select cps.* from ChartPatternSignal cps, MACDData macd \n" +
      "  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and \n" +
      "      ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and \n" +
      "      ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and \n" +
      "      ((cps.TradeType='BUY' and macd.macd >=0) or \n" +
      "      (cps.TradeType='SELL' and macd.macd <=0)) " +
      "order by TimeOfSignal";
  private final Map<String, CrossMarginPair> symbolAndIsMarginTradingAllowed = new HashMap<>();
  private final ChartPatternSignalDaoImpl dao;
  private final MACDDataDao macdDao;


  @Autowired
  public ProfitPercentageWithMoneyReuseCalculation(BinanceApiClientFactory binanceApiClientFactory,
                                                   ChartPatternSignalDaoImpl dao,
                                                   MACDDataDao macdDao) throws BinanceApiException {
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
    binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
    this.dao = dao;
    this.macdDao = macdDao;
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  double invested = 0;
  double coffer = 0.0;
  int numTrades = 0;
  double releasedFromCompletedTrades = 0.0;
  double lockedInTrades = 0.0;
  double borrowed = 0.0;
  int numTradesLive = 0;
  Iterator<Map.Entry<Date, Pair<Integer, Double>>> eventDateIterator;
  Map.Entry<Date, Pair<Integer, Double>> nextEvent;
  Set<ChartPatternSignal> tradesEnteredSoFar = new HashSet<>();

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

  public void calculate() throws BinanceApiException {
    AtomicInteger numCrossMarginPairs = new AtomicInteger();
    AtomicInteger numCrossMarginPairsBuyAllowed = new AtomicInteger();
    AtomicInteger numCrossMarginPairsSellAllowed = new AtomicInteger();
    binanceApiMarginRestClient.getCrossMarginCurrencyPairs().forEach(crossMarginPair ->{
      if (crossMarginPair.getSymbol().endsWith("USDT")) {
        symbolAndIsMarginTradingAllowed.put(crossMarginPair.getSymbol(), crossMarginPair);
        if (crossMarginPair.getIsMarginTrade()) {
          numCrossMarginPairs.incrementAndGet();
          if (crossMarginPair.getIsBuyAllowed()) {
            numCrossMarginPairsBuyAllowed.incrementAndGet();
          }
          if (crossMarginPair.getIsSellAllowed()) {
            numCrossMarginPairsSellAllowed.incrementAndGet();
          }
        }
      }
    });
    logger.info(String.format("Number of cross margin pairs=%d, buy allowed=%d, sell allowed =%d.",
        numCrossMarginPairs.get(), numCrossMarginPairsBuyAllowed.get(), numCrossMarginPairsSellAllowed.get()));

    List<ChartPatternSignal> chartPatternSignals;
    if (USE_SIGNAL_INVALIDATIONS) {
      chartPatternSignals = jdbcTemplate.query(QUERY_USING_INVALIDATIONS, new ChartPatternSignalMapper());
    } else if (useMACDForEntrySameSignAsTradeType) {
      chartPatternSignals = jdbcTemplate.query(QUERY_USING_MACD_SAME_SIGN_AS_TRADE_TYPE, new ChartPatternSignalMapper());
    } else {
      chartPatternSignals = jdbcTemplate.query(QUERY_USING_STOPLOSSES, new ChartPatternSignalMapper());
    }
    if (avoidOverdoneTradeTypes) {
      chartPatternSignals = filterOutOverdoneTradeTypesUseDynamic(chartPatternSignals);
    }
    if (USE_MARGIN) {
      List<ChartPatternSignal> chartPatternSignalsFiltered = chartPatternSignals.stream().filter(chartPatternSignal -> {
        CrossMarginPair crossMarginPair = symbolAndIsMarginTradingAllowed.get(chartPatternSignal.coinPair());
        return crossMarginPair != null && crossMarginPair.getIsMarginTrade()
            && (chartPatternSignal.tradeType() == TradeType.BUY && crossMarginPair.getIsBuyAllowed()
        || chartPatternSignal.tradeType() == TradeType.SELL && crossMarginPair.getIsSellAllowed());
      }).collect(toList());
      logger.info(String.format("Number of chart patterns originally=%d and after filtered for cross margin=%d",
          chartPatternSignals.size(), chartPatternSignalsFiltered.size()));
      chartPatternSignals = chartPatternSignalsFiltered;
    }

    if (useHistogramForEntry) {
      List<ChartPatternSignal> entryFilteredUsingMACD = getEntryFilteredUsingHistogramSameSignAsTradeType(chartPatternSignals);
      logger.info(String.format("Before filtering for entry using MACD, length was %d, and after, %d.", chartPatternSignals.size(), entryFilteredUsingMACD.size()));
      chartPatternSignals = entryFilteredUsingMACD;
    }
    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate;
    if (USE_SIGNAL_INVALIDATIONS) {
      amountsReleasedByDate = getAmountsReleaseByDateCalendar(chartPatternSignals);
    } else if (useMACDForExit) {
      amountsReleasedByDate = getAmountsReleaseByDateCalendarUsingMACDSignalCrossOverForTradeExits(chartPatternSignals);
    } else if (useHistogramTrendReversalForExit) {
      amountsReleasedByDate = getAmountsReleaseByDateCalendarUsingHistogramTrendReversalForTradeExits(chartPatternSignals);
    } else if (usePrebreakoutCandlestickPriceForStopLoss) {
      amountsReleasedByDate = getAmountsReleaseByDateCalendarUsingPrebreakoutCandlestickForStopLoss(chartPatternSignals);
    } else {
      amountsReleasedByDate = getAmountsReleaseByDateCalendarUsingStopLossStrategyNotAltfinInvalidationTime(chartPatternSignals);
    }

    eventDateIterator = amountsReleasedByDate.entrySet().iterator();
    nextEvent = eventDateIterator.next();

    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      if (!patternsWithReleaseByDateProcessed.contains(chartPatternSignal)) {
        logger.info("Skipping cps " + chartPatternSignal + " without exit time calculated");
        continue;
      }
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
      tradesEnteredSoFar.add(chartPatternSignal);
      /*
      if (!patternsWithReleaseByDateProcessed.contains(chartPatternSignal)) {
        List<ChartPatternSignal> chartPatternList = patternsNotInTargetTimeProcessedSet.get(chartPatternSignal.timeOfSignal());
        if (chartPatternList == null) {
          chartPatternList = new ArrayList<>();
          patternsNotInTargetTimeProcessedSet.put(chartPatternSignal.timeOfSignal(), chartPatternList);
        }
        chartPatternList.add(chartPatternSignal);
      }*/
    }
    // null is passed to process event dates calendar to finish.`
    processTradeExitEventsUntilGivenDate(null);
    printWallet("The end", new Date());
    System.out.println(String.format("Lowest pnlPercent=%f, hightest pnlPercent=%f.", lowestPnlPercent, highestPnlPercent));
    //dumpStateOfLockedChartPatterns();
  }

  // TODO: Have to retest this since there was a bug in getMACDDataUntilTime using DAte in the query
  // instead of Datetime.
  private List<ChartPatternSignal> getEntryFilteredUsingHistogramSameSignAsTradeType(List<ChartPatternSignal> chartPatternSignals) {
    List<ChartPatternSignal> filteredChartPatternSignals = new ArrayList<>();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      List<MACDData> macdData = macdDao.getMACDDataUntilTime(chartPatternSignal.coinPair(), chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal(), 1);
      if (macdData.isEmpty()) {
        continue;
      }
      if ((chartPatternSignal.tradeType() == TradeType.BUY && macdData.get(0).histogram > 0 ||
          chartPatternSignal.tradeType() == TradeType.SELL && macdData.get(0).histogram < 0)
          && Math.abs(macdData.get(0).histogram / macdData.get(0).macdSignal) > 0.25) {
        logger.info(String.format("'%s' eligible for entry based on MACD.", chartPatternSignal));
        filteredChartPatternSignals.add(chartPatternSignal);
        dao.setEntryEligibleBasedOnMACDSignalCrossOver(chartPatternSignal, true);
      } else {
        dao.setEntryEligibleBasedOnMACDSignalCrossOver(chartPatternSignal, false);
        logger.info(String.format("'%s' ineligible for entry based on MACD.", chartPatternSignal));
      }
    }
    return filteredChartPatternSignals;
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
            CandlestickUtil.getCandlestickStart(chartPatternSignal.timeOfSignal(), chartPatternSignal.timeFrame()),
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
    logger.info("Missing for count=" + missingForCount);
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
      Set<ChartPatternSignal> cpsExitsOnDate = cpsByExitDate.get(nextEvent.getKey());
      for (ChartPatternSignal chartPatternSignal : cpsExitsOnDate) {
        if (!tradesEnteredSoFar.contains(chartPatternSignal)) {
          logger.error("Trade never entered but getting existed: " + chartPatternSignal);
        }
      }
      numTrades += nextEvent.getValue().getFirst();
      releasedFromCompletedTrades += nextEvent.getValue().getSecond();
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

  double highestPnlPercent = 0.0, lowestPnlPercent = 0.0;
  private void printWallet(String context, Date time) {
    double totalAssetValue = coffer + lockedInTrades;
    double netAssetValue = totalAssetValue - borrowed;
    Double marginLevel = borrowed > 0? totalAssetValue / borrowed : null;
    double rr = (netAssetValue - invested) /invested * 100;
    if (invested == 0) {
      logger.error("Invested is somehow 0.");
    }
    logger.info(String.format("%s: On %s, invested=%f, coffer=%f, borrowed=%f, locked=%f, \ntotalAssetValue=%f, netAssetValue=%f, RR=%f, marginLevel=%s.",
        context, time, invested, coffer, borrowed, lockedInTrades, totalAssetValue, netAssetValue, rr,
        marginLevel != null ? marginLevel.toString() : "N/A"));

    if (rr > highestPnlPercent) {
      highestPnlPercent = rr;
    }
    if (rr < lowestPnlPercent) {
      lowestPnlPercent = rr;
    }
    //logger.info(String.format("%s: Num trades=%d, releasedFromCompletedTrades=%f, RR=%f .", time, numTrades,releasedFromCompletedTrades,  pnlPercent));
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
          (chartPatternSignal.timeOfSignalInvalidation() == null || (
              chartPatternSignal.timeOfSignalInvalidation().after(chartPatternSignal.priceTargetMetTime())
                  || chartPatternSignal.timeOfSignalInvalidation().equals(chartPatternSignal.priceTargetMetTime())))) {
        tradeExitTime = chartPatternSignal.priceTargetMetTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPotentialPercent() / 100;
      } else if (chartPatternSignal.timeOfSignalInvalidation() != null) {
        tradeExitTime = chartPatternSignal.timeOfSignalInvalidation();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPercentAtTimeOfSignalInvalidation() / 100;
      } // Fallback for when live sourcesignals signal invalidations was not caught.
      else if (chartPatternSignal.profitPercentAtSignalTargetTime() != null) {
        tradeExitTime = chartPatternSignal.priceTargetTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPercentAtSignalTargetTime() / 100;
      } else {
        // Only one signal had profitPercentAtSignalTargetTime null with IsPriceTargetMet not null.
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

  private Pair<Date, Double> getStopLossTimeAndPrice(ChartPatternSignal cps) {
    double stopLossPrice = macdDao.getStopLossLevelBasedOnBreakoutCandlestick(cps);
    Date candlestickTime = cps.timeOfSignal();
    MACDData macdData;
    do {
      macdData = macdDao.getMACDDataForCandlestick(cps.coinPair(), cps.timeFrame(), candlestickTime);
      candlestickTime = CandlestickUtil.getIthCandlestickTime(candlestickTime, cps.timeFrame(), 1);
    } while (macdData != null && (cps.tradeType() == TradeType.BUY && macdData.candleClosingPrice < stopLossPrice
    || cps.tradeType() == TradeType.SELL && macdData.candleClosingPrice > stopLossPrice));
    return macdData == null? null : Pair.of(macdData.time, macdData.candleClosingPrice);
  }

  private int missingForCount = 0;
  private TreeMap<Date, Pair<Integer, Double>> getAmountsReleaseByDateCalendarUsingPrebreakoutCandlestickForStopLoss(List<ChartPatternSignal> chartPatternSignals) {
    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate = new TreeMap<>();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      logger.info("Finding pre-breakout price for cps %s.", chartPatternSignal);
      Date tradeExitTime;
      double amountReleasedFromTheTrade;
      patternsWithReleaseByDateProcessed.add(chartPatternSignal);
      Pair<Date, Double> stopLossTimeAndPrice = getStopLossTimeAndPrice(chartPatternSignal);
      if (stopLossTimeAndPrice == null) {
        continue;
      }
      if (chartPatternSignal.isPriceTargetMet() && chartPatternSignal.priceTargetMetTime().before(stopLossTimeAndPrice.getFirst())) {
        tradeExitTime = chartPatternSignal.priceTargetMetTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPotentialPercent() / 100;
      } else if (!chartPatternSignal.isPriceTargetMet() && chartPatternSignal.priceTargetTime().before(stopLossTimeAndPrice.getFirst())) {
        tradeExitTime = chartPatternSignal.priceTargetTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPercentAtSignalTargetTime() / 100;
      } else {
        tradeExitTime = stopLossTimeAndPrice.getFirst();
        double signedStopLossPercent = -(chartPatternSignal.priceAtTimeOfSignal() - stopLossTimeAndPrice.getSecond()) / chartPatternSignal.priceAtTimeOfSignal() * 100;
        if (chartPatternSignal.tradeType() == TradeType.SELL) {
          signedStopLossPercent = -signedStopLossPercent;
        }
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE * signedStopLossPercent / 100;
      }
      Pair<Integer, Double> prevVal = amountsReleasedByDate.get(tradeExitTime);
      int tradeCount = 1;
      double amountReleased = amountReleasedFromTheTrade;
      if (prevVal != null) {
        tradeCount += prevVal.getFirst();
        amountReleased += prevVal.getSecond();
      }
      amountsReleasedByDate.put(tradeExitTime, Pair.of(tradeCount, amountReleased));
      Set<ChartPatternSignal> cpsExitsOnDate = cpsByExitDate.get(tradeExitTime);
      if (cpsExitsOnDate == null) {
        cpsExitsOnDate = new HashSet<>();
        cpsByExitDate.put(tradeExitTime, cpsExitsOnDate);
      }
      cpsExitsOnDate.add(chartPatternSignal);
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
        tradeExitTime = chartPatternSignal.priceTargetTime();
        amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE *
            chartPatternSignal.profitPercentAtSignalTargetTime() / 100;
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
      Set<ChartPatternSignal> cpsExitsOnDate = cpsByExitDate.get(tradeExitTime);
      if (cpsExitsOnDate == null) {
        cpsExitsOnDate = new HashSet<>();
        cpsByExitDate.put(tradeExitTime, cpsExitsOnDate);
      }
      cpsExitsOnDate.add(chartPatternSignal);
    }
    return amountsReleasedByDate;
  }

  // Doesn't depend on query conditon for IsPriceTargetMet. Very recent signals may not be considered for exit trade.
  private TreeMap<Date, Pair<Integer, Double>> getAmountsReleaseByDateCalendarUsingMACDSignalCrossOverForTradeExits(List<ChartPatternSignal> chartPatternSignals) {
    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate = new TreeMap<>();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      Date tradeExitTime;
      double amountReleasedFromTheTrade;
      MACDData exitMacdData = macdDao.getTradeExitSignalBySignalCrossOver(chartPatternSignal);
      if (exitMacdData == null) {
        continue;
      }
      tradeExitTime = exitMacdData.time;
      /*logger.info(String.format("Determined Exit with pnl %f percent for %s. IsPriceTargetMet=%s, Original Pnl percent=%f\n",
          getPnlPercent(chartPatternSignal, exitMacdData),
          chartPatternSignal, chartPatternSignal.isPriceTargetMet() ? "true":"false",
          chartPatternSignal.profitPotentialPercent()));*/
      amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE * getPnlPercent(chartPatternSignal, exitMacdData) / 100;
      patternsWithReleaseByDateProcessed.add(chartPatternSignal);
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

  private Date getNthCandlestickTime(Date beginCandlestick, TimeFrame timeFrame, int n) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return DateUtils.addMinutes(beginCandlestick, 15 * (n-1));
      case HOUR:
        return DateUtils.addHours(beginCandlestick, (n-1));
      case FOUR_HOURS:
        return DateUtils.addHours(beginCandlestick, 4 * (n-1));
      case DAY:
      default:
        return DateUtils.addDays(beginCandlestick, (n-1));
    }
  }

  private Map<Date, Set<ChartPatternSignal>> cpsByExitDate = new HashMap<>();
  private TreeMap<Date, Pair<Integer, Double>> getAmountsReleaseByDateCalendarUsingHistogramTrendReversalForTradeExits(List<ChartPatternSignal> chartPatternSignals) {
    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate = new TreeMap<>();
    double biggeestLossPercent = 0;
    ChartPatternSignal biggestLossCPS = null;
    MACDData biggestLossMACDData = null;
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      Date tradeExitTime;
      double amountReleasedFromTheTrade;
      MACDData exitMacdData = macdDao.getTradeExitSignalByHistogramTrendReversal(chartPatternSignal);
      if (exitMacdData == null) {
        continue;
      }
      tradeExitTime = getNthCandlestickTime(exitMacdData.time, chartPatternSignal.timeFrame(), 2);
      /*logger.info(String.format("Determined Exit with pnl %f percent for %s. IsPriceTargetMet=%s, Original Pnl percent=%f." +
              "Exit candlestick MACD=%s.\n",
          getPnlPercent(chartPatternSignal, exitMacdData),
          chartPatternSignal, chartPatternSignal.isPriceTargetMet() ? "true":"false",
          chartPatternSignal.profitPotentialPercent(), exitMacdData));*/
      double pnlPercent = getPnlPercent(chartPatternSignal, exitMacdData);
      if (pnlPercent < biggeestLossPercent) {
        biggeestLossPercent = pnlPercent;
        biggestLossCPS = chartPatternSignal;
        biggestLossMACDData = exitMacdData;
      }
      amountReleasedFromTheTrade = AMOUNT_PER_TRADE + AMOUNT_PER_TRADE * pnlPercent / 100;
      patternsWithReleaseByDateProcessed.add(chartPatternSignal);
      Pair<Integer, Double> prevVal = amountsReleasedByDate.get(tradeExitTime);
      int tradeCount = 1;
      double amountReleased = amountReleasedFromTheTrade;
      if (prevVal != null) {
        tradeCount += prevVal.getFirst();
        amountReleased += prevVal.getSecond();
      }
      amountsReleasedByDate.put(tradeExitTime, Pair.of(tradeCount, amountReleased));
      Set<ChartPatternSignal> cpsExitsOnDate = cpsByExitDate.get(tradeExitTime);
      if (cpsExitsOnDate == null) {
        cpsExitsOnDate = new HashSet<>();
        cpsByExitDate.put(tradeExitTime, cpsExitsOnDate);
      }
      cpsExitsOnDate.add(chartPatternSignal);
    }
    logger.info("Biggest loss percent for a trade=" + biggeestLossPercent + " for CPS " + biggestLossCPS
    + " Exit MACD=" + biggestLossMACDData);
    return amountsReleasedByDate;
  }

  private double getPnlPercent(ChartPatternSignal chartPatternSignal, MACDData exitMacdData) {
    int sign = chartPatternSignal.tradeType() == TradeType.BUY? 1: -1;
    double entryPrice = chartPatternSignal.priceAtTimeOfSignalReal();
    if (entryPrice == 0.0) {
      entryPrice = chartPatternSignal.priceAtTimeOfSignal();
    }
    return sign * (exitMacdData.candleClosingPrice - entryPrice)
        / chartPatternSignal.priceAtTimeOfSignal() * 100;
  }
}
