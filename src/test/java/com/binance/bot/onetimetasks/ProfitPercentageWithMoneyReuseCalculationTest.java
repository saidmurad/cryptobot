package com.binance.bot.onetimetasks;

import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.ChartPatternSignalDaoImplTest;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.SQLException;
import java.sql.Statement;

@RunWith(JUnit4.class)
public class ProfitPercentageWithMoneyReuseCalculationTest {
  /*
    @Before
    public void setUp() throws SQLException {
      new File("/home/kannanj/IdeaProjects/binance-java-api/testcryptobot.db").delete();
      SQLiteDataSource dataSource = new SQLiteDataSource();
      dataSource.setUrl("jdbc:sqlite:testcryptobot.db");
      dao = new ChartPatternSignalDaoImpl();
      dao.setDataSource(dataSource);
      Statement stmt = dataSource.getConnection().createStatement();
      stmt.execute(ChartPatternSignalDaoImplTest.createTableStmt);
    }

  @Test
  public void testCalculate() {
  }*/
}