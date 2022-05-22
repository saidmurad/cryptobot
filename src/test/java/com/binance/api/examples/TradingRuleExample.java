package com.binance.api.examples;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * sample application to print trading rules
 */
@Deprecated
public class TradingRuleExample {

    private static final String apiKey = "JWpRH4xzo9zySHIWbOH5gGwC5snn1gytOAaRxNt5cIidMTMC7HgM6lswbPEQmGig";
    private static final String secretKey = "LZdi5SFRq2p1gWeDaTP7uh4eumkI91h1Oc4lcoykGBL5KBBtysP8O0sUZmMcX1h8";
    private static final int CANDLE_LIMIT = 3;

    private static final Logger logger = LoggerFactory.getLogger(TradingRuleExample.class);

    public static void main(String[] args) throws BinanceApiException {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(apiKey, secretKey, false, false);
        BinanceApiRestClient client = factory.newRestClient();

        List<SRBounds> srBounds = readCSV();

        for(SRBounds currencyBounds: srBounds) {
            String symbol = currencyBounds.getSymbol();
            CandlestickInterval interval = currencyBounds.getInterval();

            List<Candlestick> candlestickBars = client.getCandlestickBars(symbol, interval, CANDLE_LIMIT);

            if (candlestickBars.size() == 2) {
                EventType signalType = identifyTradingSignalType(candlestickBars, currencyBounds);

                TradingSignalOutput output = generateTradingSignal(signalType, candlestickBars, currencyBounds);
                System.out.println(output.toString());
            }
        }
    }

    private static TradingSignalOutput generateTradingSignal(EventType signalType, List<Candlestick> candlestickBars, SRBounds currencyBounds) {
        Candlestick LC0 = candlestickBars.get(1);
        int LC0Position = getPositionBetweenSRBounds(LC0, currencyBounds);

        Candlestick LC1 = candlestickBars.get(0);
        int LC1Position = getPositionBetweenSRBounds(LC1, currencyBounds);

        List<Float> SRValues = currencyBounds.getBounds();

        TradingSignalOutput output = new TradingSignalOutput(currencyBounds.getSymbol(), currencyBounds.getInterval(), signalType);
        double level = -1;
        double nextLevel = -1;

        switch (signalType) {
            case BROKE_RESISTANCE:
                level = SRValues.get(LC1Position + 1);
                nextLevel = SRValues.get(LC0Position + 1);
                break;
            case BROKE_SUPPORT:
                level = SRValues.get(LC1Position);
                level = SRValues.get(LC0Position);
                break;
            case APPROACHING_RESISTANCE:
                level = SRValues.get(LC0Position + 1);
                break;
            case APPROACHING_SUPPORT:
                level = SRValues.get(LC0Position);
                break;
            case RETESTED_RESISTANCE:
                level = SRValues.get(LC1Position + 1);
                break;
            case RETESTED_SUPPORT:
                level = SRValues.get(LC1Position);
                break;
        }
        output.setLevel(level);
        output.setNextLevel(nextLevel);
        return output;
    }

    private static EventType identifyTradingSignalType(List<Candlestick> candlestickBars, SRBounds currencyBounds) {
        Candlestick LC0 = candlestickBars.get(1);
        int LC0Position = getPositionBetweenSRBounds(LC0, currencyBounds);

        Candlestick LC1 = candlestickBars.get(0);
        int LC1Position = getPositionBetweenSRBounds(LC1, currencyBounds);

        // check for price crossing scenarios
        if (LC0Position > LC1Position) {
            return EventType.BROKE_RESISTANCE;
        } else if (LC0Position < LC1Position) {
            return EventType.BROKE_SUPPORT;
        } else {
            // not crossed, check for approaching scenarios
            // LC0Position == LC1Position

            double LC0Close = Double.parseDouble(LC0.getClose());
            double LC1Close = Double.parseDouble(LC1.getClose());

            double nearestResistance = currencyBounds.getBounds().get(LC0Position + 1);
            double nearestSupport = currencyBounds.getBounds().get(LC0Position);

            if (LC0Close > LC1Close) {
                // going upwards
                if (LC1Close * 0.99 <= nearestSupport && LC0Close * 1.01 < nearestResistance) {
                    return EventType.RETESTED_SUPPORT;
                } else {
                    return EventType.APPROACHING_RESISTANCE;
                }
            } else if (LC0Close < LC1Close) {
                // going downwards
                if (LC1Close * 1.01 >= nearestResistance && LC0Close * 0.99 > nearestSupport) {
                    return EventType.RETESTED_RESISTANCE;
                } else {
                    return EventType.APPROACHING_SUPPORT;
                }
            }
        }
        return EventType.UNDEFINED;
    }

    private static int getPositionBetweenSRBounds(Candlestick latestCandle, SRBounds currencyBounds) {
        double price = Double.parseDouble(latestCandle.getClose());
        List<Float> bounds = currencyBounds.getBounds();

        for(int i=0; i<bounds.size()-1; i++) {
            if (bounds.get(i) <= price && bounds.get(i+1) >= price) {
                // correct place
                return i;
            }
        }

        return -1;
    }


    public static List<SRBounds> readCSV() {
        List<SRBounds> data = new ArrayList<>();
        URL csvResource = TradingRuleExample.class.getClassLoader().getResource("sr_prices.csv");

        if (csvResource != null) {
            try {
                Scanner scanner = new Scanner(new File(csvResource.getFile()));
                while (scanner.hasNext())
                {
                    String[] split = scanner.nextLine().strip().split(",");

                    if (split.length >= 4) {
                        String symbol = split[0];
                        CandlestickInterval interval = CandlestickInterval.valueOf(split[1].strip());

                        String[] stringBounds = Arrays.copyOfRange(split, 2, split.length);
                        List<Float> bounds = Arrays.stream(stringBounds).map(s -> Float.parseFloat(s.strip())).collect(Collectors.toList());

                        SRBounds levels = new SRBounds(symbol, interval, bounds);
                        data.add(levels);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                logger.error(e.getMessage());
            }
        }
        return data;
    }

    private static class SRBounds {
        private String symbol;
        private CandlestickInterval interval;
        private List<Float> bounds;

        public SRBounds(String symbol, CandlestickInterval interval, List<Float> bounds) {
            this.symbol = symbol;
            this.interval = interval;
            this.bounds = bounds.stream().sorted().collect(Collectors.toList());
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public CandlestickInterval getInterval() {
            return interval;
        }

        public void setInterval(CandlestickInterval interval) {
            this.interval = interval;
        }

        public List<Float> getBounds() {
            return bounds;
        }

        public void setBounds(List<Float> bounds) {
            this.bounds = bounds;
        }
    }

    private static class TradingSignalOutput {
        private String symbol;
        private CandlestickInterval interval;
        private EventType eventType;
        private double level;
        private double nextLevel;

        public TradingSignalOutput(String symbol, CandlestickInterval interval, EventType eventType) {
            this.symbol = symbol;
            this.interval = interval;
            this.eventType = eventType;
        }

        public TradingSignalOutput(String symbol, CandlestickInterval interval, EventType eventType, double level, double nextLevel) {
            this.symbol = symbol;
            this.interval = interval;
            this.eventType = eventType;
            this.level = level;
            this.nextLevel = nextLevel;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public CandlestickInterval getInterval() {
            return interval;
        }

        public void setInterval(CandlestickInterval interval) {
            this.interval = interval;
        }

        public EventType getEventType() {
            return eventType;
        }

        public void setEventType(EventType eventType) {
            this.eventType = eventType;
        }

        public double getLevel() {
            return level;
        }

        public void setLevel(double level) {
            this.level = level;
        }

        public double getNextLevel() {
            return nextLevel;
        }

        public void setNextLevel(double nextLevel) {
            this.nextLevel = nextLevel;
        }

        @Override
        public String toString() {
            return "TradingSignalOutput{" +
                    "symbol='" + symbol + '\'' +
                    ", interval=" + interval +
                    ", eventType=" + eventType +
                    ", level=" + level +
                    ", nextLevel=" + nextLevel +
                    '}';
        }
    }


    private enum EventType {
        BROKE_SUPPORT,
        BROKE_RESISTANCE,
        APPROACHING_SUPPORT,
        APPROACHING_RESISTANCE,
        RETESTED_SUPPORT,
        RETESTED_RESISTANCE,
        UNDEFINED
    }
}
