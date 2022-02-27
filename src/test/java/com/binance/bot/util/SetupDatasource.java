package com.binance.bot.util;

import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.util.TimeZone;

public class SetupDatasource {
  public static DataSource getDataSource() {
    new File("/home/kannanj/IdeaProjects/binance-java-api/testcryptobot.db").delete();
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:testcryptobot.db");
    return dataSource;
  }
}
