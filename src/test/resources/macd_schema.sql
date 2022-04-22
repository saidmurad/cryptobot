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