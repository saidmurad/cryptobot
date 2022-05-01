package com.binance.bot.util;

import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.util.TimeZone;

public class CreateDatasource {
  public static DataSource createDataSource() {
    //new File("/home/kannanj/IdeaProjects/cryptobot/testcryptobot.db").delete();
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:testcryptobot.db");
    return dataSource;
  }
}
