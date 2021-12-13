package com.binance.bot.chartpattern;

import com.altfins.TradeType;

import java.util.Date;

public class ChartPatternSignal {
    // Example: BTC
    private String coin;

    private Date signalOccurenceTime;

    private TradeType tradeType;

    // The type of the candle stick chart where the signal originated.
    enum CandleStickType {
        FIFTEEN_MIN,
        HOURLY,
        FOUR_HOURLY,
        DAILY
    }
    private CandleStickType candleStickType;

    // Deadline for the price target to reach
    private Date signalExpiry;

    // Type of the pattern that originated the trade signal, such as "Resistance", "Falling wedge"
    private String patternName;

    // The price of the coin at the time of the signal, if known. For example the resistance level that
    // was broke. For triangular breakouts for exmaple, the price of breakout is not known from the source
    // that originates our signals
    private double priceAtSignalIssueTime;

    // The target price predicted for the pattern
    private double priceTarget;
}

