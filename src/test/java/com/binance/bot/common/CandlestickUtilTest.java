package com.binance.bot.common;

import com.binance.bot.tradesignals.TimeFrame;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class CandlestickUtilTest {

  private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Before
  public void setUp() {
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Test
  public void getCandlestickStart_fifteenMinutes() throws ParseException {
    assertThat(CandlestickUtil.getCandlestickStart(df.parse("2022-05-29 13:47:05"), TimeFrame.FIFTEEN_MINUTES))
        .isEqualTo(df.parse("2022-05-29 13:45:00"));
  }

  @Test
  public void getCandlestickStart_hourly() throws ParseException {
    assertThat(CandlestickUtil.getCandlestickStart(df.parse("2022-05-29 13:47:05"), TimeFrame.HOUR))
        .isEqualTo(df.parse("2022-05-29 13:00:00"));
  }

  @Test
  public void getCandlestickStart_fourHourly() throws ParseException {
    assertThat(CandlestickUtil.getCandlestickStart(df.parse("2022-05-29 13:47:05"), TimeFrame.FOUR_HOURS))
        .isEqualTo(df.parse("2022-05-29 12:00:00"));
  }

  @Test
  public void getCandlestickStart_daily() throws ParseException {
    assertThat(CandlestickUtil.getCandlestickStart(df.parse("2022-05-29 13:47:05"), TimeFrame.DAY))
        .isEqualTo(df.parse("2022-05-29 00:00:00"));
  }
}