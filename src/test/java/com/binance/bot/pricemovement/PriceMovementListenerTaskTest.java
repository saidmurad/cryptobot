package com.binance.bot.pricemovement;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.PriceMovement;
import com.binance.api.client.domain.PriceMovementDirection;
import com.binance.api.client.domain.SRPriceDetail;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
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

    /*
     * SR = 3300f, 3400f, 3500f, 3600f, 3700f
     *
     * LC1 price < LC0 price ( price increasing )
     *
     * scenario 1       price crossing resistance
     *                  ex - LC1 < 3500, LC0 >= 3500
     *                  outcome = BROKE_RESISTANCE
     *
     * scenario 2       LC1 close to (1%) support, LC0 close to (1%) resistance
     *                  ex - LC1 <= 3434, LC0 >= 3465
     *                  outcome = AT_RESISTANT
     *
     *                  LC1 not close to support, LC0 close (1%) to resistance
     *                  ex - LC1 > 3434, LC0 >= 3465
     *                  outcome - AT_RESISTANCE
     *
     * scenario 3       LC1 close to (1%) support, LC0 not close to resistance
     *                  ex - LC1 <= 3434, LC0 < 3465
     *                  outcome = RETESTED_SUPPORT
     *
     * scenario 4       LC1 not close to support, LC0 not close to resistance
     *                  ex - LC1 > 3434, LC0 < 3465
     *                  outcome - APPROACHING_RESISTANCE
     *
     * LC1 price > LC0 price ( price decreasing )
     *
     * scenario 5       price crossing support
     *                  ex - LC1 > 3500, LC0 <= 3400
     *                  outcome - BROKE_SUPPORT
     *
     * scenario 6       LC1 close to (1%) resistance, LC0 close to (1%) support
     *                  ex - LC1 >= 3465, LC0 <= 3434
     *                  outcome - AT_SUPPORT
     *
     *                  LC1 not close to resistance, LC0 close to (1%) support
     *                  ex - LC1 < 3465, LC <= 3434
     *                  outcome - AT_SUPPORT
     *
     *  scenario 7      LC1 close to (1%) resistance, LC0 not close to support
     *                  ex - LC1 >= 3465, LC > 3434
     *                  outcome - RETESTED_RESISTANCE
     *
     * scenario 8       LC1 not close to resistance, LC0 not close to support
     *                  ex - LC1 < 3465, LC0 > 3434
     *                  outcome - APPROACHING_SUPPORT
     */


    // scenario 1
    @Test
    public void testPerform_BTCUSDT_brokeResistance_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3450");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3501");
        Candlestick C = new Candlestick();    // latest incomplete candlestick
        C.setClose("3800");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.BROKE_RESISTANCE, output.getMovementDirection());
        assertEquals(3500, output.getLevel(), 0.001);
        assertEquals(3600, output.getNextLevel(), 0.001);
    }

    // scenario 2
    @Test
    public void testPerform_BTCUSDT_atResistance_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3434");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3465");
        Candlestick C = new Candlestick();      // latest candlestick
        C.setClose("3800");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.AT_RESISTANCE, output.getMovementDirection());
        assertEquals(3500, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    // scenario 3
    @Test
    public void testPerform_BTCUSDT_retestedSupport_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3434");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3464.9");
        Candlestick C = new Candlestick();    // latest candlestick
        C.setClose("3400");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.RETESTED_SUPPORT, output.getMovementDirection());
        assertEquals(3400, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    // scenario 4
    @Test
    public void testPerform_BTCUSDT_approachingResistance_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3434.1");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3464.9");
        Candlestick C = new Candlestick();    // latest incomplete candlestick
        C.setClose("3800");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.APPROACHING_RESISTANCE, output.getMovementDirection());
        assertEquals(3500, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    // scenario 5
    @Test
    public void testPerform_BTCUSDT_brokeSupport_daily() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.DAILY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3501");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3499");
        Candlestick C = new Candlestick();    // latest candlestick
        C.setClose("3999");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.BROKE_SUPPORT, output.getMovementDirection());
        assertEquals(3500, output.getLevel(), 0.001);
        assertEquals(3400, output.getNextLevel(), 0.001);
    }

    // scenario 6
    @Test
    public void testPerform_BTCUSDT_atSupport_fourHourly() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.FOUR_HOURLY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3450");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3434");
        Candlestick C = new Candlestick();    // latest candlestick
        C.setClose("3401");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.AT_SUPPORT, output.getMovementDirection());
        assertEquals(3400, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    // scenario 7
    @Test
    public void testPerform_BTCUSDT_retestedResistance_daily() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.DAILY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3465");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3434.1");
        Candlestick C = new Candlestick();    // latest candlestick
        C.setClose("3200");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.RETESTED_RESISTANCE, output.getMovementDirection());
        assertEquals(3500, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }

    // scenario 8
    @Test
    public void testPerform_BTCUSDT_approachingSupport_daily() throws BinanceApiException {
        String symbol = "BTCUSDT";
        CandlestickInterval interval = CandlestickInterval.DAILY;

        Candlestick LC1 = new Candlestick();    // last-1 candlestick
        LC1.setClose("3464");
        Candlestick LC0 = new Candlestick();    // last candlestick
        LC0.setClose("3434.1");
        Candlestick C = new Candlestick();    // latest candlestick
        C.setClose("3200");

        List<Candlestick> candlestickList = new ArrayList<>();
        candlestickList.add(LC1);
        candlestickList.add(LC0);
        candlestickList.add(C);

        when(mockBinanceApiRestClient.getCandlestickBars(symbol, interval, 3)).thenReturn(candlestickList);

        SRPriceDetail priceDetail = new SRPriceDetail(symbol, interval, Arrays.asList(3300f, 3400f, 3500f, 3600f, 3700f));

        PriceMovement output = priceMovementListenerTask.generatePriceMovement(priceDetail);
        assertEquals(symbol, output.getSymbol());
        assertEquals(interval, output.getInterval());
        assertEquals(PriceMovementDirection.APPROACHING_SUPPORT, output.getMovementDirection());
        assertEquals(3400, output.getLevel(), 0.001);
        assertEquals(-1, output.getNextLevel(), 0.001);
    }
}
