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

create table BitcoinPriceMonitoring(
      Time TEXT not Null, timeFrame TEXT not Null, candleOpenPrice REAL, candleClosePrice REAL,
      TradeTypeOverdone TEXT,
      Constraint PK Primary Key(time, timeFrame));

alter statements pending:
1. alter table ChartPatternSignal add column TradeExitType TEXT;
isInsertedLate no longer used.
2. priceAtTimeOfSignalReal is no longe rused as the entery marekt order will give that price.
2. exit columns for stop loss replace previous limit order column ExitLimitOrderId, not used now. need to drop unused cols for limit exit.
alter table ChartPatternSignal add column ExitStopLossOrderId INTEGER;
alter table ChartPatternSignal add column ExitStopLossOrderExecutedQty REAL;
alter table ChartPatternSignal add column ExitStopLossAvgPrice REAL;
alter table ChartPatternSignal add column ExitStopLossOrderStatus TEXT;
