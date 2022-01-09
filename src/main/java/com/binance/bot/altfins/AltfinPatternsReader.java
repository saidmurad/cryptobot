package com.binance.bot.altfins;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Reads the patterns output by the Python code.
 */
@Component
public class AltfinPatternsReader implements Runnable {

  static final String[] patternsFiles = {"/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns1.txt",
      "/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns2.txt",
      "/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns3.txt",
      "/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns4.txt"};
  private long[] lastProcessedTimes = new long[4];
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Override
  public void run() {
    while (true) {
      try {
        for (int i =0; i < 4; i++) {
          File file = new File(patternsFiles[i]);
          if (lastProcessedTimes[i] == 0 || lastProcessedTimes[i] < file.lastModified()) {
            List<ChartPatternSignal> patterns = readPatterns(new String(Files.readAllBytes(file.toPath())));
            logger.info(MessageFormat.format("Read {0} patterns for timeframe {1} for file modified at {2}.", patterns.size(), i, dateFormat.format(new Date(file.lastModified()))));
            lastProcessedTimes[i] = file.lastModified();
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
    List<ChartPatternSignal> patterns = new GsonBuilder()
        .registerTypeAdapter(listType, new ChartPatternSignalDeserializer())
        .create()
        .fromJson(content, listType);
    return patterns;
  }
}
