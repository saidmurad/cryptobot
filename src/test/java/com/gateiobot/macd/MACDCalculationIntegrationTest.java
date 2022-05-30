package com.gateiobot.macd;

import com.binance.bot.tradesignals.TimeFrame;
import com.gateiobot.db.MACDData;
import io.gate.gateapi.ApiException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@EnableConfigurationProperties
public class MACDCalculationIntegrationTest {
  @Autowired
  MACDCalculation macdCalculation;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Before
  public void setUp() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Test
  public void getMACD() throws ParseException, ApiException {
    List<MACDData> macdData = macdCalculation.getMACDData("BTC_USDT", dateFormat.parse("2022-05-31 00:00:00"), TimeFrame.DAY);
    System.out.println(macdData);
  }
}
