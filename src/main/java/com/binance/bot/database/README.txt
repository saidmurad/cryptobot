sqlite3
open .BotTrading

Create Table ChartPatternSignal(
    CoinPair TEXT NOT NULL,
    TimeFrame TEXT NOT NULL,
    TradeType TEXT NOT NULL,
    Pattern TEXT NOT NULL,
    PriceAtTimeOfSignal REAL NOT NULL,
    PriceRelatedToPattern REAL NOT NULL,
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
    PriceBestReached REAL,
    PriceCurrent REAL,
    CurrentTime TEXT,
    PRIMARY KEY (CoinPair, TimeFrame, TradeType, Pattern, TimeOfSignal)
);
