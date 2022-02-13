Create Table ChartPatternSignal(
    CoinPair TEXT NOT NULL,
    TimeFrame TEXT NOT NULL,
    TradeType TEXT NOT NULL,
    Pattern TEXT NOT NULL,
    PriceAtTimeOfSignal REAL NOT NULL,
    PriceRelatedToPattern REAL,
    TimeOfSignal TEXT NOT NULL,
    PriceTarget REAL NOT NULL,
    PriceTargetTime TEXT NOT NULL,
    ProfitPotentialPercent REAL NOT NULL,
    IsSignalOn INTEGER,
    VolumeAtSignalCandlestick INTEGER,
    VolumeAverage REAL,
    IsVolumeSurge INTEGER,
    TimeOfSignalInvalidation TEXT,
    ReasonForSignalInvalidation TEXT,
    PriceAtSignalTargetTime REAL,
    PriceAtTenCandlestickTime REAL,
    ProfitPercentAtTenCandlestickTime REAL,
    PriceBestReached REAL,
    PriceCurrent REAL,
    CurrentTime TEXT
);
PRIMARY KEY (CoinPair, TimeFrame, TradeType, Pattern, TimeOfSignal)


alter statements pending:
1. isInsertedLate no longer used.
2. priceAtTimeOfSignalReal is no longe rused as the entery marekt order will give that price.
2. exit columns for stop loss replace previous limit order column ExitLimitOrderId, not used now. need to drop unused cols for limit exit.

