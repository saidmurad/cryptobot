Create Table ChartPatternSignal(
CoinPair TEXT NOT NULL,
TimeFrame TEXT NOT NULL,
TradeType TEXT NOT NULL,
Pattern TEXT NOT NULL,
Attempt INTEGER NOT NULL,
PriceAtTimeOfSignal REAL NOT NULL,
PriceAtTimeOfSignalReal REAL,
PriceRelatedToPattern REAL,
TimeOfSignal TEXT NOT NULL,
TimeOfInsertion TEXT,
IsInsertedLate INTEGER,
PriceTarget REAL NOT NULL,
PriceTargetTime TEXT NOT NULL,
ProfitPotentialPercent REAL NOT NULL,
IsSignalOn INTEGER,
NumTimesMissingInInput INTEGER,
VolumeAtSignalCandlestick INTEGER,
VolumeAverage REAL,
IsVolumeSurge INTEGER,
TimeOfSignalInvalidation TEXT,
PriceAtTimeOfSignalInvalidation REAL,
ProfitPercentAtTimeOfSignalInvalidation REAL,
ReasonForSignalInvalidation TEXT,
PriceAtSignalTargetTime REAL,
ProfitPercentAtSignalTargetTime REAL,
TenCandlestickTime TEXT,
PriceAtTenCandlestickTime REAL,
FailedToGetPriceAtTenCandlestickTime INTEGER,
FailedToGetPriceAtSignalTargetTime INTEGER,
ProfitPercentAtTenCandlestickTime REAL,
PriceBestReached REAL,
PriceCurrent REAL,
CurrentTime TEXT,
EntryOrderId INTEGER,
EntryExecutedQty REAL,
EntryAvgPrice REAL,
EntryOrderStatus TEXT,
ExitStopLossOrderId INTEGER,
ExitStopLossOrderExecutedQty REAL,
ExitStopLossAvgPrice REAL,
ExitStopLossOrderStatus TEXT,
ExitOrderId INTEGER,
ExitOrderExecutedQty REAL,
ExitOrderAvgPrice REAL,
ExitOrderStatus TEXT,
Realized REAL,
RealizedPercent REAL,
UnRealized REAL,
UnRealizedPercent REAL,
IsPositionExited INTEGER,
ErrorMessage TEXT,
LastUpdatedTime TEXT,
MaxLoss REAL,
MaxLossPercent REAL,
MaxLossTime TEXT,
StopLossTime TEXT,
TwoPercentLossTime TEXT,
FivePercentLossTime TEXT,
PreBreakoutCandlestickStopLossPrice REAL,
StopLossPrice REAL,
IsPriceTargetMet INTEGER,
PriceTargetMetTime REAL,
TradeExitType TEXT,
EntryEligibleBasedOnMACDSignalCrossOver INTEGER,
CONSTRAINT chartpatternsignal_pk PRIMARY KEY (CoinPair, TimeFrame, TradeType, TimeOfSignal, Attempt)
);

create table ChartPatternSignalInvalidationEvents(CoinPair TEXT not null, TimeFrame text not null, TradeType text not null, Pattern Text not null, TimeOfSignal Text not null, InvalidationEventTime TEXT not null, Event TEXT not null);

create table BitcoinPriceMonitoring(
      Time TEXT not Null, timeFrame TEXT not Null, candleOpenPrice REAL, candleClosePrice REAL,
      TradeTypeOverdone TEXT,
      Constraint PK Primary Key(time, timeFrame));

create table CrossMarginAccountFundingHistory(
    Time TEXT not NULL,
    Principal REAL not NULL
);

insert into CrossMarginAccountFundingHistory values('2022-04-01 00:00', 12345);

create table CrossMarginAccountBalanceHistory(
    Time TEXT not NULL,
    FreeUSDT INTEGER not NULL,
    LockedUSDT INTEGER not NULL,
    BorrowedUSDT INTEGER NOT NULL,
    NetUSDT INTEGER not NULL,
    TotalValue INTEGER not NULL,
    LiabilityValue INTEGER not NULL,
    NetValue INTEGER not NULL,
    MarginLevel REAL not NULL,
    ReturnRate REAL
);

create table NumOutstandingTrades(
    TimeFrame TEXT NOT NULL,
    NumOutstandingTrades INTEGER,
    Constraint PK Primary Key(TimeFrame)
);
insert into NumOutstandingTrades values('FIFTEEN_MINUTES', 0);
insert into NumOutstandingTrades values('HOUR', 0);
insert into NumOutstandingTrades values('FOUR_HOURS', 0);
insert into NumOutstandingTrades values('DAY', 0);

create table MACDData(
CoinPair TEXT,
TimeFrame TEXT not null,
Time TEXT not null,
CandleClosingPrice REAL not null,
SMA REAL not null,
Trend TEXT not null,
SMASlope REAL not null,

EMA26 REAL,
EMA12 REAL,
MACD REAL,
MACDSignal REAL,
Histogram REAL,
HistogramEMA REAL,
HistogramTrendType TEXT
);