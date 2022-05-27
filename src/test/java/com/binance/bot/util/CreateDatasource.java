package com.binance.bot.util;

import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.util.TimeZone;

public class CreateDatasource {
  public static DataSource createDataSource() {
    File testdbfile = new File("testcryptobot.db");
    if (testdbfile.exists()) {
      testdbfile.delete();
    }
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:testcryptobot.db");
    return dataSource;
  }
}
