package com.binance.bot.pricemovement;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.PriceMovement;
import com.binance.api.client.domain.PriceMovementDirection;
import com.binance.api.client.domain.SRPriceDetail;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PriceMovementListenerTask {
    @Value("${price.dataset.path}")
    private String srDatasetPath;

    private static final long FOUR_HOURS = 4 * 60 * 60 * 1000;
    private static final long DAILY = 24 * 60 * 60 * 1000;
    private static final int CANDLE_LIMIT = 3;

    private final BinanceApiRestClient binanceApiRestClient;
    private final Logger logger;

    private List<SRPriceDetail> fourHourlySRPrices;
    private List<SRPriceDetail> dailySRPrices;

    @Autowired
    PriceMovementListenerTask(BinanceApiClientFactory binanceApiClientFactory) {
        this.binanceApiRestClient = binanceApiClientFactory.newRestClient();
        this.logger = LoggerFactory.getLogger(getClass());

        this.fourHourlySRPrices = Collections.emptyList();
        this.dailySRPrices = Collections.emptyList();

        // test purpose only
//        if (srDatasetPath == null || srDatasetPath.isEmpty()) {
//            this.srDatasetPath = "E:\\FREELANCE\\UPWORK\\cryptobot\\src\\test\\resources\\sr_prices.csv";
//        }
    }

    /**
     * schedules repeated task to read csv file with support-resistance price points
     * and classify them according to candlestick interval time
     */
//    @Scheduled(fixedRate = DAILY, initialDelayString = "${timing.initialDelay}")
    @Scheduled(fixedRate = DAILY)
    private void dailySRPriceLoader() {
        List<SRPriceDetail> priceDetails = readCSVInput();

        if (!priceDetails.isEmpty()) {
            this.fourHourlySRPrices = priceDetails.stream()
                    .filter(prices -> CandlestickInterval.FOUR_HOURLY.equals(prices.getInterval()))
                    .collect(Collectors.toList());

            this.dailySRPrices = priceDetails.stream()
                    .filter(prices -> CandlestickInterval.DAILY.equals(prices.getInterval()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * read CSV file and load support-resistance price levels for each provided currency type
     *
     * @return price details object containing SR prices list, for each record in the CSV file
     */
    public List<SRPriceDetail> readCSVInput() {
        List<SRPriceDetail> data = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(srDatasetPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] split = line.strip().split(",");
                if (split.length >= 4) {
                    String symbol = split[0];
                    CandlestickInterval interval = CandlestickInterval.valueOf(split[1].strip());

                    String[] stringBounds = Arrays.copyOfRange(split, 2, split.length);
                    List<Float> bounds = Arrays.stream(stringBounds).map(s -> Float.parseFloat(s.strip())).collect(Collectors.toList());

                    SRPriceDetail levels = new SRPriceDetail(symbol, interval, bounds);
                    data.add(levels);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }

    /**
     * scheduled task runs per 4 hours
     * executes price movement output generator method for each 4-hour SR data record
     */
//    @Scheduled(fixedRate = DAILY, initialDelayString = "${timing.initialDelay}")
    @Scheduled(fixedRate = DAILY)
    public void dailyObservation() {
        dailySRPrices.forEach( priceDetail -> {
            PriceMovement output = generatePriceMovement(priceDetail);
            logPriceMovementOutput(output);
        });
    }

    /**
     * scheduled task runs daily
     * executes price movement output generator method for each daily SR data record
     */
//    @Scheduled(fixedRate = FOUR_HOURS, initialDelayString = "${timing.initialDelay}")
    @Scheduled(fixedRate = 10000)
    public void fourHourlyObservation() {
        fourHourlySRPrices.forEach( priceDetail -> {
            PriceMovement output = generatePriceMovement(priceDetail);
            logPriceMovementOutput(output);
        });
    }

    /**
     * given support-resistance price point details, output price movement object
     * price movement direction details are derived from real-time binance candle stick data API
     * TODO: method is made public for unit testing purposes
     *
     * @param priceDetail price movement object
     */
    public PriceMovement generatePriceMovement(SRPriceDetail priceDetail) {
        String symbol = priceDetail.getSymbol();
        CandlestickInterval interval = priceDetail.getInterval();

        List<Candlestick> candlestickBars;
        try {
            candlestickBars = binanceApiRestClient.getCandlestickBars(symbol, interval, CANDLE_LIMIT);

            PriceMovementDirection signalType = identifyTradingSignalType(candlestickBars, priceDetail);

            return generatePriceMovementOutput(signalType, candlestickBars, priceDetail);

        } catch (BinanceApiException e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    /**
     * TODO: change this logic appropriately
     *
     * @param output price movement output object
     */
    private void logPriceMovementOutput(PriceMovement output) {
        if (output != null) {
            logger.info(output.toString());
        }
    }

    /**
     * generate full price movement direction(including pricing levels)
     * given price movement direction and other past data
     *
     * @param direction       price movement direction
     * @param candlestickBars last two candle stick data
     * @param priceDetail     SR price point details
     * @return full detailed price movement object
     */
    private PriceMovement generatePriceMovementOutput(PriceMovementDirection direction,
                                                      List<Candlestick> candlestickBars, SRPriceDetail priceDetail) {
        if (!PriceMovementDirection.UNDEFINED.equals(direction)) {
            Candlestick LC0 = candlestickBars.get(1);
            int LC0Position = getPositionBetweenSRPricePoints(LC0, priceDetail);

            Candlestick LC1 = candlestickBars.get(0);
            int LC1Position = getPositionBetweenSRPricePoints(LC1, priceDetail);

            List<Float> SRValues = priceDetail.getBounds();

            PriceMovement output = new PriceMovement(priceDetail.getSymbol(), priceDetail.getInterval(), direction);

            double level = -1;
            double nextLevel = -1;

            switch (direction) {
                case BROKE_RESISTANCE:
                    level = SRValues.get(LC1Position + 1);
                    nextLevel = SRValues.get(LC0Position + 1);
                    break;
                case BROKE_SUPPORT:
                    level = SRValues.get(LC1Position);
                    nextLevel = SRValues.get(LC0Position);
                    break;
                case APPROACHING_RESISTANCE:
                case AT_RESISTANCE:
                    level = SRValues.get(LC0Position + 1);
                    break;
                case APPROACHING_SUPPORT:
                case AT_SUPPORT:
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
        return null;
    }

    /**
     * derive price movement direction from past candle stick data
     *
     *
     * @param candlestickBars latest three candle stick data
     *                        last one of them represents the current/incomplete candle which is neglected in current scenario
     * @param priceDetail     SR price point details for the currency
     * @return price movement direction
     */
    private PriceMovementDirection identifyTradingSignalType(List<Candlestick> candlestickBars, SRPriceDetail priceDetail) {
        if (candlestickBars.size() == CANDLE_LIMIT) {
            // latest candle stick
            Candlestick LC0 = candlestickBars.get(1);
            int LC0Position = getPositionBetweenSRPricePoints(LC0, priceDetail);

            // one before the latest candle stick
            Candlestick LC1 = candlestickBars.get(0);
            int LC1Position = getPositionBetweenSRPricePoints(LC1, priceDetail);

            // check for price crossing scenarios
            if (LC0Position > LC1Position) {
                return PriceMovementDirection.BROKE_RESISTANCE;
            } else if (LC0Position < LC1Position) {
                return PriceMovementDirection.BROKE_SUPPORT;
            } else {
                // not crossed, check for approaching scenarios
                // LC0Position == LC1Position

                double LC0Close = Double.parseDouble(LC0.getClose());
                double LC1Close = Double.parseDouble(LC1.getClose());

                double nearestResistance = priceDetail.getBounds().get(LC0Position + 1);
                double nearestSupport = priceDetail.getBounds().get(LC0Position);

                if (LC0Close > LC1Close) {
                    // going upwards
                    if (LC1Close <= nearestSupport * 1.01 && LC0Close < nearestResistance * 0.99) {
                        return PriceMovementDirection.RETESTED_SUPPORT;
                    } else if (LC0Close >= nearestResistance * 0.99){
                        return PriceMovementDirection.AT_RESISTANCE;
                    } else {
                        return PriceMovementDirection.APPROACHING_RESISTANCE;
                    }
                } else if (LC0Close < LC1Close) {
                    // going downwards
                    if (LC1Close >= nearestResistance * 0.99 && LC0Close > nearestSupport * 1.01) {
                        return PriceMovementDirection.RETESTED_RESISTANCE;
                    } else if (LC0Close <= nearestSupport * 1.01){
                        return PriceMovementDirection.AT_SUPPORT;
                    } else {
                        return PriceMovementDirection.APPROACHING_SUPPORT;
                    }
                }
            }
        }
        return PriceMovementDirection.UNDEFINED;
    }

    /**
     * given ordered list of past support-resistance price points and a candle stick object
     * finds the position of the closing price, between SR price points
     *
     * @param LC          last candle stick
     * @param priceDetail SR price point details
     * @return position of the closing price, between SR price points
     */
    private int getPositionBetweenSRPricePoints(Candlestick LC, SRPriceDetail priceDetail) {
        double price = Double.parseDouble(LC.getClose());
        List<Float> bounds = priceDetail.getBounds();

        for (int i = 0; i < bounds.size() - 1; i++) {
            if (bounds.get(i) <= price && bounds.get(i + 1) >= price) {
                return i;
            }
        }
        return -1;
    }
}
