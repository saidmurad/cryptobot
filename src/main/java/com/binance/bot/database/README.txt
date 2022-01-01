sqlite3
open .BotTrading

Create Table ChartPatternSignal(
    CoinPair TEXT NOT NULL,
    TimeFrame TEXT NOT NULL,
    Pattern TEXT NOT NULL,
    PriceAtTimeOfSignal REAL NOT NULL,
    TimeOfSignal TEXT NOT NULL,
    PriceTarget REAL NOT NULL,
    ProfitPotentialPercent REAL,
    PRIMARY KEY (CoinPair, TimeFrame, Pattern, TimeOfSignal)
);
