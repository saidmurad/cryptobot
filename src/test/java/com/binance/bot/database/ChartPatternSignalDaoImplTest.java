package com.binance.bot.database;

import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;

public class ChartPatternSignalDaoImplTest extends TestCase {

  ChartPatternSignalDaoImpl dao;
  String createTableStmt = "Create Table ChartPatternSignal(\n" +
      "    CoinPair TEXT NOT NULL,\n" +
      "    TimeFrame TEXT NOT NULL,\n" +
      "    TradeType TEXT NOT NULL,\n" +
      "    Pattern TEXT NOT NULL,\n" +
      "    PriceAtTimeOfSignal REAL NOT NULL,\n" +
      "    PriceRelatedToPattern REAL NOT NULL,\n" +
      "    TimeOfSignal TEXT NOT NULL,\n" +
      "    PriceTarget REAL NOT NULL,\n" +
      "    PriceTargetTime TEXT NOT NULL,\n" +
      "    ProfitPotentialPercent REAL NOT NULL,\n" +
      "    IsSignalOn INTEGER,\n" +
      "    VolumeAtSignalCandlestick INTEGER,\n" +
      "    VolumeAverage REAL,\n" +
      "    IsVolumeSurge INTEGER,\n" +
      "    TimeOfSignalInvalidation TEXT,\n" +
      "    ReasonForSignalInvalidation TEXT,\n" +
      "    PriceAtSignalTargetTime REAL,\n" +
      "    PriceAtTenCandlestickTime REAL,\n" +
      "    PriceBestReached REAL,\n" +
      "    PriceCurrent REAL,\n" +
      "    CurrentTime TEXT,\n" +
      "    PRIMARY KEY (CoinPair, TimeFrame, TradeType, Pattern, TimeOfSignal)\n" +
      ");";

  @Before
  public void setUp() throws SQLException {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:testcryptobot.db");
    dao = new ChartPatternSignalDaoImpl();
    dao.setDataSource(dataSource);
    Statement stmt = dataSource.getConnection().createStatement();
    stmt.execute(createTableStmt);
  }

  @Test
  public void testInsertChartPatternSignal() {

  }
}