package com.binance.bot.altfins;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.BinanceTradingBot;
import com.binance.bot.trading.GetVolumeProfile;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.binance.bot.trading.VolumeProfile;
import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads the patterns output by the Python code.
 */
@Component
public class AltfinPatternsReader {

  static final String[] patternsFiles = {"data_patterns1.txt",
      "data_patterns2.txt",
      "data_patterns3.txt",
      "data_patterns4.txt"};
  private static final String PROD_MACHINE_DIR = "/usr/local/google/home/kannanj/altfins/send_alerts";
  private static final String DEV_MACHINE_DIR = "/home/kannanj";
  private final TimeFrame[] timeFrames = {TimeFrame.FIFTEEN_MINUTES, TimeFrame.HOUR, TimeFrame.FOUR_HOURS, TimeFrame.DAY};
  private final BinanceTradingBot binanceTradingBot;
  private long[] lastProcessedTimes = new long[4];
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final String ALTFINS_PATTERNS_DIR;
  private final BinanceApiRestClient restClient;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final ChartPatternSignalDaoImpl chartPatternSignalDao;
  @Autowired
  AltfinPatternsReaderBookkeeping bookkeeping;
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  private GetVolumeProfile getVolumeProfile;
  @Value("${fifteen_minute_timeframe}")
  String fifteenMinuteTimeFrameAllowedTradeTypeConfig;
  @Value("${hourly_timeframe}")
  String hourlyTimeFrameAllowedTradeTypeConfig;
  @Value("${four_hourly_timeframe}")
  String fourHourlyTimeFrameAllowedTradeTypeConfig;
  @Value("${daily_timeframe}")
  String dailyTimeFrameAllowedTradeTypeConfig;

  @Autowired
  public AltfinPatternsReader(BinanceApiClientFactory binanceApiClientFactory, GetVolumeProfile getVolumeProfile,
                              ChartPatternSignalDaoImpl chartPatternSignalDao, BinanceTradingBot binanceTradingBot,
                              SupportedSymbolsInfo supportedSymbolsInfo) {
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (new File(PROD_MACHINE_DIR).exists()) {
      ALTFINS_PATTERNS_DIR = PROD_MACHINE_DIR;
    } else {
      ALTFINS_PATTERNS_DIR = DEV_MACHINE_DIR;
    }
    restClient = binanceApiClientFactory.newRestClient();
    this.getVolumeProfile = getVolumeProfile;
    this.chartPatternSignalDao = chartPatternSignalDao;
    this.binanceTradingBot = binanceTradingBot;
  }

  @Scheduled(fixedDelay = 60000)
  // TODO: Add unit tests.
  public void run() throws IOException {
    try {
      for (int i =0; i < 4; i++) {
        File file = new File(ALTFINS_PATTERNS_DIR + "/" + patternsFiles[i]);
        if (lastProcessedTimes[i] == 0 || lastProcessedTimes[i] < file.lastModified()) {
          byte[] fileBytes = Files.readAllBytes(file.toPath());
          if (fileBytes == null) {
            logger.warn("Read an empty file. Ignoring.");
            continue;
          }
          String altfinPatternsStr = new String(fileBytes);
          List<ChartPatternSignal> patternFromAltfins = readPatterns(altfinPatternsStr);
          if (patternFromAltfins == null) {
            logger.error("Got null from reading patterns from altfinPatternsStr: " + altfinPatternsStr);
            continue;
          }
          if (patternFromAltfins.size() == 0) {
            logger.warn("Read empty array. Ignoring");
            continue;
          }
          String tmpAltfinsPatternsFilePath = "/tmp/" + patternsFiles[i] + dateFormat.format(new Date(file.lastModified()));
          if (Files.exists(Path.of(tmpAltfinsPatternsFilePath))) {
            Files.delete(Path.of(tmpAltfinsPatternsFilePath));
          }
          Files.copy(file.toPath(), Path.of(tmpAltfinsPatternsFilePath));
          logger.info(MessageFormat.format("Read {0} patterns for timeframe {1} for file modified at {2}.", patternFromAltfins.size(), i, dateFormat.format(new Date(file.lastModified()))));
          lastProcessedTimes[i] = file.lastModified();
          processPaterns(patternFromAltfins, timeFrames[i]);
        }
      }
    } catch (IOException |ParseException ex) {
      logger.error("Exception.", ex);
      throw new RuntimeException(ex);
    }
    HeartBeatChecker.logHeartBeat(getClass());
  }

  // TODO: Unit test.
  void processPaterns(List<ChartPatternSignal> patternFromAltfins, TimeFrame timeFrame) throws ParseException {
    patternFromAltfins = makeUnique(patternFromAltfins);
    int origSize = patternFromAltfins.size();
    List<ChartPatternSignal> temp = patternFromAltfins.stream()
        .filter(chartPatternSignal -> supportedSymbolsInfo.getSupportedSymbols().contains(chartPatternSignal.coinPair()))
        .collect(Collectors.toList());
    if (patternFromAltfins.size() < origSize) {
      logger.info(String.format("Filtered out %d symbols not supported on Binance: %s.", (origSize - patternFromAltfins.size()),
          getCoinPairsInDifferenceBetween(patternFromAltfins, temp)));
    }
    patternFromAltfins = temp;
    if (patternFromAltfins.size() == 0) {
      logger.info("Left with empty patterns list now.");
    }
    List<ChartPatternSignal> chartPatternsInDB = chartPatternSignalDao.getAllChartPatterns(timeFrame);
    List<ChartPatternSignal> chartPatternsThatAreBack = getChartPatternSignalsThatAreBack(patternFromAltfins, chartPatternsInDB);
    printPatterns(chartPatternsThatAreBack, "Chart Patterns back with their incremented attempt counts", LogLevel.WARN);
    int ti = getTimeframeIndex(timeFrame);
    List<ChartPatternSignal> newChartPatternSignals = getNewChartPatternSignals(
        chartPatternsInDB, patternFromAltfins);
    if (!newChartPatternSignals.isEmpty()) {
      logger.info(String.format("Received %d new chart patterns for time frame %s.", newChartPatternSignals.size(), timeFrame.name()));
    }
    newChartPatternSignals.addAll(chartPatternsThatAreBack);
    if (!newChartPatternSignals.isEmpty()) {
      for (ChartPatternSignal chartPatternSignal : newChartPatternSignals) {
        insertNewChartPatternSignal(chartPatternSignal);
        if (bookkeeping.earliestChartPatternTimesInThisRun[ti] == null || bookkeeping.earliestChartPatternTimesInThisRun[ti].after(chartPatternSignal.timeOfSignal())) {
          bookkeeping.earliestChartPatternTimesInThisRun[ti] = chartPatternSignal.timeOfSignal();
        }
      }
    }

    List<ChartPatternSignal> invalidatedChartPatternSignals = getChartPatternSignalsToInvalidate(
        patternFromAltfins, chartPatternsInDB);
    if (!invalidatedChartPatternSignals.isEmpty()) {
      logger.info(String.format("Invalidating %d chart pattern signals for time frame %s.",
          invalidatedChartPatternSignals.size(), timeFrame.name()));

      for (ChartPatternSignal chartPatternSignal : invalidatedChartPatternSignals) {
        ReasonForSignalInvalidation reasonForInvalidation =
            // For comeback signals we don't know when they got resurrected by Altfins if that time occurred prior to
            // the program started running.
            chartPatternSignal.attempt() > 1 ||
            bookkeeping.earliestChartPatternTimesInThisRun[ti] != null &&
                (chartPatternSignal.timeOfSignal().equals(bookkeeping.earliestChartPatternTimesInThisRun[ti]) ||
                chartPatternSignal.timeOfSignal().after(bookkeeping.earliestChartPatternTimesInThisRun[ti]))
            ? ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS :
                ReasonForSignalInvalidation.BACKLOG_AND_COLD_START;
        double priceAtTimeOfInvalidation = 0;
        if (reasonForInvalidation == ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS) {
          priceAtTimeOfInvalidation = numberFormat.parse(
              restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
          logger.info("Obtained price " + priceAtTimeOfInvalidation + " from Binance");
        }
        boolean ret = chartPatternSignalDao.invalidateChartPatternSignal(
            chartPatternSignal, priceAtTimeOfInvalidation, reasonForInvalidation);
        logger.info("Invalidated chart pattern signal " + chartPatternSignal + " with ret val" + ret);
      }
    }
  }

  private int getTimeframeIndex(TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return 0;
      case HOUR:
        return 1;
      case FOUR_HOURS:
        return 2;
      case DAY:
      default:
        return 3;
    }
  }

  boolean isTradingAllowed(TimeFrame timeFrame, TradeType tradeType) {
    String configForTimeFrame;
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        configForTimeFrame = fifteenMinuteTimeFrameAllowedTradeTypeConfig;
        break;
      case HOUR:
        configForTimeFrame = hourlyTimeFrameAllowedTradeTypeConfig;
        break;
      case FOUR_HOURS:
        configForTimeFrame = fourHourlyTimeFrameAllowedTradeTypeConfig;
        break;
      default:
        configForTimeFrame = dailyTimeFrameAllowedTradeTypeConfig;
    }
    switch (configForTimeFrame) {
      case "NONE":
        return false;
      case "BOTH":
        return true;
      case "BUY":
        return tradeType == TradeType.BUY;
      case "SELL":
      default:
        return tradeType == TradeType.SELL;
    }
  }

  private String getCoinPairsInDifferenceBetween(List<ChartPatternSignal> patternFromAltfins, List<ChartPatternSignal> postFilterPatterns) {
    StringBuilder stringBuilder = new StringBuilder();
    Set<ChartPatternSignal> postFilterPatternsSet = new HashSet<>();
    postFilterPatternsSet.addAll(postFilterPatterns);
    patternFromAltfins.stream().filter(chartPatternSignal -> !postFilterPatternsSet.contains(chartPatternSignal))
        .forEach(chartPatternSignal -> {
          stringBuilder.append(chartPatternSignal.coinPair() + ", ");
        });
    return stringBuilder.toString();
  }

  private void printPatterns(List<ChartPatternSignal> patterns, String s, LogLevel logLevel) {
    if (patterns.isEmpty()) {
      return;
    }
    switch (logLevel) {
      case ERROR:
        logger.error("\n" + s);
        for (ChartPatternSignal c : patterns) {
          logger.error(c.toString());
          logger.error("Time of invalidation: " + dateFormat.format(c.timeOfSignalInvalidation()));
        }
        logger.error("\n");
        break;
      default:
        logger.info("\n" + s);
        for (ChartPatternSignal c : patterns) {
          logger.info(c.toString());
        }
        logger.info("\n");
    }
  }

  void insertNewChartPatternSignal(ChartPatternSignal chartPatternSignal) throws ParseException {
    Date currTime = new Date();
    boolean isInsertedLate;
    if (chartPatternSignal.attempt() > 1) {
      // For comeeback signals we don't have a reliable way of detecting that we caught the comeback late.
      isInsertedLate = false;
    } else {
      isInsertedLate = isInsertedLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal(), currTime);
    }
    chartPatternSignal = ChartPatternSignal.newBuilder().copy(chartPatternSignal)
        .setTimeOfInsertion(currTime)
        .setPriceAtTimeOfSignalReal(numberFormat.parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue())
        .setTenCandlestickTime(new Date(chartPatternSignal.timeOfSignal().getTime() + Util.getTenCandleStickTimeIncrementMillis(chartPatternSignal)))
        .setIsInsertedLate(isInsertedLate)
        .build();
    VolumeProfile volProfile = getVolumeProfile.getVolumeProfile(chartPatternSignal.coinPair());
    //logger.info("Inserting chart pattern signal " + chartPatternSignal);
    boolean ret = chartPatternSignalDao.insertChartPatternSignal(chartPatternSignal, volProfile);
    //logger.info("Ret value: " + ret);
    // TODO: Unit test.
    if (!isInsertedLate && isTradingAllowed(chartPatternSignal.timeFrame(), chartPatternSignal.tradeType())) {
      binanceTradingBot.placeTrade(chartPatternSignal);
    }
  }

  // TODO: Unit test
  private boolean isInsertedLate(TimeFrame timeFrame, Date timeOfSignal, Date currTime) {
    long timeLagMins = (currTime.getTime() - timeOfSignal.getTime()) / 60000;
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return timeLagMins > 15;
      case HOUR:
        return timeLagMins > 30;
      case FOUR_HOURS:
        return timeLagMins > 30;
      default:
        return timeLagMins > 120;
    }
  }

  private List<ChartPatternSignal> makeUnique(List<ChartPatternSignal> patterns) {
    Set<ChartPatternSignal> signalSet = new HashSet<>();
    signalSet.addAll(patterns);
    List<ChartPatternSignal> condensedList = signalSet.stream().collect(Collectors.toList());
    logger.info(String.format("Condensed %d patterns to %d after removing duplicates.", patterns.size(), condensedList.size()));
    if (signalSet.size() < patterns.size()) {
      printDuplicatePatterns(patterns);
    }
    return condensedList;
  }

  private void printDuplicatePatterns(List<ChartPatternSignal> patterns) {
    Map<ChartPatternSignal, Integer> counts = new HashMap<>();
    for (ChartPatternSignal chartPatternSignal: patterns) {
      Integer count = counts.get(chartPatternSignal);
      if (count == null) {
        count = 0;
      }
      count ++;
      counts.put(chartPatternSignal, count);
    }
    List<Map.Entry<ChartPatternSignal, Integer>> duplicates = counts.entrySet().stream().filter(entry -> entry.getValue() > 1).collect(Collectors.toList());
    for (Map.Entry<ChartPatternSignal, Integer> entry : duplicates) {
      logger.warn(String.format("ChartPatternSignal %s occurred %d times.", entry.getKey().toString(), entry.getValue()));
    }
  }

  public List<ChartPatternSignal> readPatterns(String content) {
    Type listType = new TypeToken<List<ChartPatternSignal>>(){}.getType();
    List<ChartPatternSignal> patterns;
    try {
      patterns = new GsonBuilder()
          .registerTypeAdapter(listType, new ChartPatternSignalDeserializer())
          .create()
          .fromJson(content, listType);
    } catch (JsonSyntaxException e) {
      logger.warn("Faced JsonSyntaxException and ignoring.", e);
      patterns = new ArrayList<>();
    }
    return patterns;
  }

  List<ChartPatternSignal> getNewChartPatternSignals(List<ChartPatternSignal> listToCheckAgainst, List<ChartPatternSignal> listToCheck) {
    Set<ChartPatternSignal> signalsInTableSet = new HashSet<>();
    signalsInTableSet.addAll(listToCheckAgainst);
    return listToCheck.stream().filter(chartPatternSignal -> !signalsInTableSet.contains(chartPatternSignal))
        .collect(Collectors.toList());
  }

  // Which chart pattern signals I marked as invalidated again comes in the input with the same time of occurence of signal.
  List<ChartPatternSignal> getChartPatternSignalsThatAreBack(List<ChartPatternSignal> patternsFromAltfins,
                                                             List<ChartPatternSignal> allPatternsInDB) {
    Set<ChartPatternSignal> altfinPaternSignals = new HashSet<>();
    altfinPaternSignals.addAll(patternsFromAltfins);
    List<ChartPatternSignal> invalidatedPatternsInDBThatHaveComeBack = allPatternsInDB.stream().filter(
        chartPatternSignal -> !chartPatternSignal.isSignalOn() &&
            altfinPaternSignals.contains(chartPatternSignal))
        .collect(Collectors.toList());
    // Calculate the highest attempt count for each comeback pattern in the DB.
    Map<ChartPatternSignal, ChartPatternSignal> comebackPatternsMap = new HashMap<>();
    invalidatedPatternsInDBThatHaveComeBack.forEach(comebackPattern -> {
      if (comebackPatternsMap.containsKey(comebackPattern)) {
        if (comebackPattern.attempt() > comebackPatternsMap.get(comebackPattern).attempt()) {
          comebackPatternsMap.put(comebackPattern, comebackPattern);
        }
      } else {
        comebackPatternsMap.put(comebackPattern, comebackPattern);
      }
    });
    // Iterate over the altfin patterns again to pick comeback patterns with previous count in db + 1.
    List<ChartPatternSignal> comebackPatterns = new ArrayList<>();
    for (ChartPatternSignal patternFromAltfins : patternsFromAltfins) {
      if (comebackPatternsMap.containsKey(patternFromAltfins)) {
        ChartPatternSignal highestAttemptedPrev = comebackPatternsMap.get(patternFromAltfins);
        comebackPatterns.add(ChartPatternSignal.newBuilder().copy(highestAttemptedPrev)
            .setAttempt(highestAttemptedPrev.attempt() + 1)
            .build());
      }
    }
    return comebackPatterns;
  }

  List<ChartPatternSignal> getChartPatternSignalsToInvalidate(List<ChartPatternSignal> patternsFromAltfins, List<ChartPatternSignal> allPatternsInDB) {
    Set<ChartPatternSignal> patternsFromAltfinsSet = new HashSet<>();
    patternsFromAltfinsSet.addAll(patternsFromAltfins);
    List<ChartPatternSignal> chartPatternsMissingInInput = allPatternsInDB.stream().filter(chartPatternSignal ->
            chartPatternSignal.isSignalOn() && !patternsFromAltfinsSet.contains(chartPatternSignal))
        .collect(Collectors.toList());
    chartPatternSignalDao.incrementNumTimesMissingInInput(chartPatternsMissingInInput);

    //printSuspiciousRemovals(patternsFromAltfins, chartPatternsMissingInInput, altfinPatternsStr, tmpAltfinsPatternsFilePath);
    /*Map<ChartPatternSignal, ChartPatternSignal> allPatternsInDBMap = new HashMap<>();
    allPatternsInDB.stream().forEach(patternInDB -> {
      allPatternsInDBMap.put(patternInDB, patternInDB);
    });
    List<ChartPatternSignal> chartPatternSignalsReappearedInTime = new ArrayList<>();
    patternsFromAltfins.stream().forEach(patternFromAltfins -> {
      ChartPatternSignal patternInDB = allPatternsInDBMap.get(patternFromAltfins);
      if (patternInDB != null && patternInDB.isSignalOn() && patternInDB.numTimesMissingInInput() > 0) {
        chartPatternSignalsReappearedInTime.add(patternInDB);
      }
    });
    chartPatternSignalDao.resetNumTimesMissingInInput(chartPatternSignalsReappearedInTime);*/

    return chartPatternSignalDao.getChartPatternSignalsToInvalidate();
  }

  private void printSuspiciousRemovals(List<ChartPatternSignal> patternsFromAltfins, List<ChartPatternSignal> chartPatternsMissingInInput, String altfinPatternsStr, String tmpAltfinsPatternsFilePath) {
    TimeFrame timeFrame = patternsFromAltfins.get(0).timeFrame();
    for (ChartPatternSignal patternFromAltfin: patternsFromAltfins) {
      for (ChartPatternSignal patternMissing: chartPatternsMissingInInput) {
        if (patternFromAltfin.coinPair().equals(patternMissing.coinPair()) && patternFromAltfin.pattern().equals(patternMissing.pattern()) && patternFromAltfin.tradeType() == patternMissing.tradeType()) {
          logger.error("Suspicious removal in timeframe " + timeFrame.name() + " of pattern:\n" + patternMissing.toString() + "\nShould have matched input pattern:\n" + patternFromAltfin.toString());
        }
      }
    }

    for (ChartPatternSignal patternMissing: chartPatternsMissingInInput) {
      if (altfinPatternsStr.contains(patternMissing.coinPair())) {
        logger.error("Suspicious removal in timeframe " + timeFrame.name() + " of pattern for coin pair " + patternMissing.coinPair() + " as it is seen in the altfins patterns file " + tmpAltfinsPatternsFilePath);
      }
    }
  }
}
