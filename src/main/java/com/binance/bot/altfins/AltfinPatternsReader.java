package com.binance.bot.altfins;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.trading.GetVolumeProfile;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.binance.bot.trading.VolumeProfile;
import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
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
public class AltfinPatternsReader implements Runnable {

  static final String[] patternsFiles = {"data_patterns1.txt",
      "data_patterns2.txt",
      "data_patterns3.txt",
      "data_patterns4.txt"};
  private static final String PROD_MACHINE_DIR = "/usr/local/google/home/kannanj/altfins/send_alerts";
  private static final String DEV_MACHINE_DIR = "/home/kannanj";
  private final TimeFrame[] timeFrames = {TimeFrame.FIFTEEN_MINUTES, TimeFrame.HOUR, TimeFrame.FOUR_HOURS, TimeFrame.DAY};
  private long[] lastProcessedTimes = new long[4];
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final String ALTFINS_PATTERNS_DIR;
  private final BinanceApiRestClient restClient;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final ChartPatternSignalDaoImpl chartPatternSignalDao;

  @Autowired
  private SupportedSymbolsInfo supportedSymbolsInfo;
  @Autowired
  private GetVolumeProfile getVolumeProfile;

  public AltfinPatternsReader(BinanceApiClientFactory binanceApiClientFactory, GetVolumeProfile getVolumeProfile, ChartPatternSignalDaoImpl chartPatternSignalDao) {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (new File(PROD_MACHINE_DIR).exists()) {
      ALTFINS_PATTERNS_DIR = PROD_MACHINE_DIR;
    } else {
      ALTFINS_PATTERNS_DIR = DEV_MACHINE_DIR;
    }
    restClient = binanceApiClientFactory.newRestClient();
    this.getVolumeProfile = getVolumeProfile;
    this.chartPatternSignalDao = chartPatternSignalDao;
  }

  @Override
  // TODO: Add unit tests.
  public void run() {
    boolean coldStart = true;
    while (true) {
      try {
        for (int i =0; i < 4; i++) {
          File file = new File(ALTFINS_PATTERNS_DIR + "/" + patternsFiles[i]);
          if (lastProcessedTimes[i] == 0 || lastProcessedTimes[i] < file.lastModified()) {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            if (fileBytes == null) {
              // Read file at the wrong time.
              logger.warn("Read an empty file. Ignoring.");
              continue;
            }
            List<ChartPatternSignal> patternFromAltfins = readPatterns(new String(fileBytes));
            if (patternFromAltfins.size() == 0) {
              logger.warn("Read empty array. Ignoring");
              continue;
            }
            logger.info(MessageFormat.format("Read {0} patterns for timeframe {1} for file modified at {2}.", patternFromAltfins.size(), i, dateFormat.format(new Date(file.lastModified()))));
            patternFromAltfins = makeUnique(patternFromAltfins);
            int origSize = patternFromAltfins.size();
            patternFromAltfins = patternFromAltfins.stream()
                .filter(chartPatternSignal -> supportedSymbolsInfo.getSupportedSymbols().containsKey(chartPatternSignal.coinPair()))
                .collect(Collectors.toList());
            if (patternFromAltfins.size() < origSize) {
              logger.info(String.format("Filtered out %d symbols not supported on Binance.", (origSize - patternFromAltfins.size())));
            }
            lastProcessedTimes[i] = file.lastModified();
            if (patternFromAltfins.size() == 0) {
              logger.info("Left with empty patterns list now.");
              continue;
            }
            List<ChartPatternSignal> chartPatternsInDB = chartPatternSignalDao.getAllChartPatterns(timeFrames[i]);
            List<ChartPatternSignal> newChartPatternSignals = getNewChartPatternSignals(chartPatternsInDB, patternFromAltfins);
            if (!newChartPatternSignals.isEmpty()) {
              logger.info(String.format("Received %d new chart patterns for time frame %s.", newChartPatternSignals.size(), timeFrames[i].name()));
              for (ChartPatternSignal chartPatternSignal : newChartPatternSignals) {
                insertNewChartPatternSignal(chartPatternSignal);
              }
            }

            List<ChartPatternSignal> invalidatedChartPatternSignals = getChartPatternSignalsToInvalidate(patternFromAltfins, chartPatternsInDB);
            if (!invalidatedChartPatternSignals.isEmpty()) {
              ReasonForSignalInvalidation reasonForInvalidation = coldStart ? ReasonForSignalInvalidation.BACKLOG_AND_COLD_START : ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS;
              logger.info(String.format("Invalidating %d chart pattern signals for time frame %s for reason %s.", invalidatedChartPatternSignals.size(), timeFrames[i].name(), reasonForInvalidation.name()));
              for (ChartPatternSignal chartPatternSignal : invalidatedChartPatternSignals) {
                double priceAtTimeOfInvalidation = 0;
                if (reasonForInvalidation == ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS) {
                  priceAtTimeOfInvalidation = numberFormat.parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
                }
                boolean ret = chartPatternSignalDao.invalidateChartPatternSignal(chartPatternSignal, priceAtTimeOfInvalidation, reasonForInvalidation);
                logger.info("Invalidated chart pattern signal " + chartPatternSignal + " in DB with ret val " + ret);
              }
            }
          }
        }
        coldStart = false;
        Thread.sleep(60000);
      } catch (InterruptedException | IOException | ParseException e) {
        logger.error("Exception.", e);
        throw new RuntimeException(e);
      }
    }
  }

  void insertNewChartPatternSignal(ChartPatternSignal chartPatternSignal) {
    VolumeProfile volProfile = getVolumeProfile.getVolumeProfile(chartPatternSignal.coinPair());
    logger.info("Inserting chart pattern signal " + chartPatternSignal);
    boolean ret = chartPatternSignalDao.insertChartPatternSignal(chartPatternSignal, volProfile);
    logger.info("Ret value: " + ret);
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
      logger.warn(String.format("ChartPatternSignal %s occured %d times.", entry.getKey().toString(), entry.getValue()));
    }
  }

  List<ChartPatternSignal> readPatterns(String content) {
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

  List<ChartPatternSignal> getChartPatternSignalsToInvalidate(List<ChartPatternSignal> patternsFromAltfins, List<ChartPatternSignal> allPatternsInDB) {
    Set<ChartPatternSignal> patternsFromAltfinsSet = new HashSet<>();
    patternsFromAltfinsSet.addAll(patternsFromAltfins);
    return allPatternsInDB.stream().filter(chartPatternSignal ->
          chartPatternSignal.isSignalOn() && !patternsFromAltfinsSet.contains(chartPatternSignal))
        .collect(Collectors.toList());
  }
}
