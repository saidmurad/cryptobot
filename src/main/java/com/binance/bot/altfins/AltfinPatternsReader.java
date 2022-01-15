package com.binance.bot.altfins;

import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads the patterns output by the Python code.
 */
@Component
public class AltfinPatternsReader implements Runnable {

  static final String[] patternsFiles = {"/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns1.txt",
      "/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns2.txt",
      "/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns3.txt",
      "/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns4.txt"};
  private final TimeFrame[] timeFrames = {TimeFrame.FIFTEEN_MINUTES, TimeFrame.HOUR, TimeFrame.FOUR_HOURS, TimeFrame.DAY};
  private long[] lastProcessedTimes = new long[4];
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Autowired
  private ChartPatternSignalDaoImpl chartPatternSignalDao;
  
  @Override
  public void run() {
    while (true) {
      try {
        for (int i =0; i < 4; i++) {
          File file = new File(patternsFiles[i]);
          if (lastProcessedTimes[i] == 0 || lastProcessedTimes[i] < file.lastModified()) {
            List<ChartPatternSignal> patternFromAltfins = readPatterns(new String(Files.readAllBytes(file.toPath())));
            logger.info(MessageFormat.format("Read {0} patterns for timeframe {1} for file modified at {2}.", patternFromAltfins.size(), i, dateFormat.format(new Date(file.lastModified()))));
            lastProcessedTimes[i] = file.lastModified();
            if (patternFromAltfins.size() == 0) {
              logger.warn("Read empty array. Ignoring");
              continue;
            }
            List<ChartPatternSignal> chartPatternsInDB = chartPatternSignalDao.getActiveChartPatterns(timeFrames[i]);
            List<ChartPatternSignal> newChartPatternSignals = getChartPatternSignalsDelta(chartPatternsInDB, patternFromAltfins);
            for (ChartPatternSignal chartPatternSignal: newChartPatternSignals) {
              boolean ret = chartPatternSignalDao.insertChartPatternSignal(chartPatternSignal);
              logger.info("Inserted chart pattern signal " + chartPatternSignal + " into DB with ret val '" + ret + "'");
            }

            List<ChartPatternSignal> invalidatedChartPatternSignals = getChartPatternSignalsDelta(patternFromAltfins, chartPatternsInDB);
            for (ChartPatternSignal chartPatternSignal: invalidatedChartPatternSignals) {
              boolean ret = chartPatternSignalDao.invalidateChartPatternSignal(chartPatternSignal, ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS);
              logger.info("Invalidated chart pattern signal " + chartPatternSignal + " in DB with ret val '" + ret + "'");
            }
          }
        }
        Thread.sleep(60000);
      } catch (InterruptedException | IOException e) {
        logger.error("Exception.", e);
        throw new RuntimeException(e);
      }
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

  List<ChartPatternSignal> getChartPatternSignalsDelta(List<ChartPatternSignal> listToCheckAgainst, List<ChartPatternSignal> listToCheck) {
    Set<ChartPatternSignal> signalsInTableSet = new HashSet<>();
    signalsInTableSet.addAll(listToCheckAgainst);
    return listToCheck.stream().filter(chartPatternSignal -> !signalsInTableSet.contains(chartPatternSignal))
        .collect(Collectors.toList());
  }
}
