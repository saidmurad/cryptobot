package com.binance.bot.onetimetasks;

import com.binance.bot.database.ChartPatternSignalMapper;
import com.binance.bot.tradesignals.ChartPatternSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ProfitPercentageWithMoneyReuseCalculation {
  @Autowired
  private JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final static double AMOUNT_PER_TRADE = 10;
  private static final String QUERY = "select * from ChartPatternSignal where " +
      "DateTime(TimeOfSignal)>=DateTime('2022-02-08 08:55') " +
      "order by TimeOfSignal";
  public ProfitPercentageWithMoneyReuseCalculation() {
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

  public void calculate() {
    List<ChartPatternSignal> chartPatternSignals = jdbcTemplate.query(QUERY, new ChartPatternSignalMapper());

    TreeMap<Date, Pair<Integer, Double>> amountsReleasedByDate = getAmountsReleaseByDateCalendar(chartPatternSignals);

    eventDateIterator = amountsReleasedByDate.entrySet().iterator();
    nextEvent = eventDateIterator.next();

    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      processTradeExitEventsUntilGivenDate(chartPatternSignal.timeOfSignal());
      if (coffer >= AMOUNT_PER_TRADE) {
        coffer -= AMOUNT_PER_TRADE;
        lockedInTrades += AMOUNT_PER_TRADE;
        printWallet("Entering trade reusing from coffer", chartPatternSignal.timeOfSignal());
      } else if ((coffer + lockedInTrades + AMOUNT_PER_TRADE) / (borrowed + AMOUNT_PER_TRADE) > 1.5) {
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
    processTradeExitEventsUntilGivenDate(null);
    printWallet("The end", new Date());
    dumpStateOfLockedChartPatterns();
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
      logger.info(String.format("Releasing %f from %d trades.", nextEvent.getValue().getSecond(), nextEvent.getValue().getFirst()));
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
}
