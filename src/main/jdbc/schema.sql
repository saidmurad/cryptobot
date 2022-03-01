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

create table CrossMarginAccountFundingHistory(
    Time TEXT not NULL,
    Principal REAL not NULL
);

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


alter statements pending:
isInsertedLate no longer used.
2. priceAtTimeOfSignalReal is no longe rused as the entery marekt order will give that price.
2. exit columns for stop loss replace previous limit order column ExitLimitOrderId, not used now. need to drop unused cols for limit exit.
