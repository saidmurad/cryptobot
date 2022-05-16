package com.binance.bot.pricemovement;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.PriceMovement;
import com.binance.api.client.domain.PriceMovementDirection;
import com.binance.api.client.domain.SRPriceDetail;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class PriceMovementListenerTaskTest {
    @Mock private BinanceApiClientFactory mockBinanceApiClientFactory;
    @Mock private BinanceApiRestClient mockBinanceApiRestClient;

    private PriceMovementListenerTask priceMovementListenerTask;

    @Before
    public void setup() throws BinanceApiException {
        mockBinanceApiClientFactory = Mockito.mock(BinanceApiClientFactory.class);
        mockBinanceApiRestClient = Mockito.mock(BinanceApiRestClient.class);

        when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);

        priceMovementListenerTask = new PriceMovementListenerTask(mockBinanceApiClientFactory);
    }

    @Test
    public void testPerform_BTCUSDT_brokeResistance_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("35400");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("35700");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 2)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(30000f, 32000f, 35500f, 36000f, 37000f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.BROKE_RESISTANCE, output.getMovementDirection());
        assertEquals(35500, output.getLevel(), 0.001);
        assertEquals(36000, output.getNextLevel(), 0.001);
    }


    @Test
    public void testPerform_BTCUSDT_brokeSupport_daily() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.DAILY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("37100");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("35700");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 2)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(30000f, 32000f, 35500f, 36000f, 37000f, 38000f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.BROKE_SUPPORT, output.getMovementDirection());
        assertEquals(37000, output.getLevel(), 0.001);
        assertEquals(35500, output.getNextLevel(), 0.001);
    }

    /*
     * last candle closing price < last-1 candle closing price
     * possible combinations,
     * scenario 1       last-1 candle close to (1%) resistance, last candle close to (1%) support
     *                  last-1 = 35900, last = 35100
     *                  outcome = APPROACHING_SUPPORT
     * scenario 2       last-1 candle close to (1%) resistance, last candle NOT close to support
     *                  last-1 = 35900, last = 35500
     *                  outcome = RETESTED_RESISTANCE
     * scenario 3       last-1 candle NOT close to resistance, last candle NOT close to support
     *                  last-1 = 35600, last = 35400
     *                  outcome = APPROACHING_SUPPORT
     * scenario 4       last-1 candle NOT close to resistance, last candle close to (1%) support
     *                  last-1 = 35600, last = 35100
     *                  outcome = APPROACHING_SUPPORT
     */

    @Test
    public void testPerform_BTCUSDT_approachingSupport_daily() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.DAILY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("35600");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("35400");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 2)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(30000f, 32000f, 35000f, 36000f, 37000f, 38000f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.APPROACHING_SUPPORT, output.getMovementDirection());
        assertEquals(35000, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    @Test
    public void testPerform_BTCUSDT_retestedResistance_daily() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.DAILY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("35900");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("35400");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 2)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(30000f, 32000f, 35000f, 36000f, 37000f, 38000f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.RETESTED_RESISTANCE, output.getMovementDirection());
        assertEquals(36000, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    @Test
    public void testPerform_BTCUSDT_approachingResistance_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("35400");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("35900");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 2)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(30000f, 32000f, 35000f, 36000f, 37000f, 38000f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.APPROACHING_RESISTANCE, output.getMovementDirection());
        assertEquals(36000, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    @Test
    public void testPerform_BTCUSDT_retestingSupport_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("35100");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("35500");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 2)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(30000f, 32000f, 35000f, 36000f, 37000f, 38000f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.RETESTED_SUPPORT, output.getMovementDirection());
        assertEquals(35000, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }
}
