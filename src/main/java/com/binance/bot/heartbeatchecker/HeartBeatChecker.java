package com.binance.bot.heartbeatchecker;

import java.io.FileWriter;
import java.io.IOException;

public class HeartBeatChecker {
  public static void logHeartBeat(Class clazz) throws IOException {
    FileWriter fw = new FileWriter(clazz.getSimpleName() + ".heartbeat");
    fw.write("hohoho");
    fw.close();
  }
}
