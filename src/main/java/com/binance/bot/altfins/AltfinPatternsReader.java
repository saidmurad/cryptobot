package com.binance.bot.altfins;

import com.binance.bot.processsignals.ProcessSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/**
 * Reads the patterns output by the Python code.
 */
public class AltfinPatternsReader implements Runnable {

  private static final String FIFTEEN_MIN_PATTERNS_FILE = "/usr/local/google/home/kannanj/altfins/send_alerts/data_patterns1.txt";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Gson gson = new Gson();

  @Override
  public void run() {
    long lastProcessTime = 0;
    while (true) {
      try {
        File fifteenMinPatternsFile = new File(FIFTEEN_MIN_PATTERNS_FILE);
        if (lastProcessTime == 0 || fifteenMinPatternsFile.lastModified() > lastProcessTime) {
          lastProcessTime = fifteenMinPatternsFile.lastModified();
        }
        Thread.sleep(60000);
      } catch (InterruptedException | IOException e) {
        logger.error("Exception.", e);
      }
    }
  }
  
  void readPatterns(Path filePath) throws IOException {
    String content = Files.readString(filePath);
    gson
  }
}
