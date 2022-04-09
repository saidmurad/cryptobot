sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> ((cps.TradeType='BUY' and macd.HistogramTrendType='ACCELERATING') or
   ...> (cps.TradeType='SELL' and macd.HistogramTrendType='DECELERATING')) and
   ...> (cps.TimeFrame = 'HOUR' or cps.TimeFrame='FOUR_HOURS')
   ...> group by cps.TimeFrame, IsPriceTargetMet;
FOUR_HOURS|0|956
FOUR_HOURS|1|1064
HOUR|0|3443
HOUR|1|3365
sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> ((cps.TradeType='BUY' and macd.macd >=0) or
   ...> (cps.TradeType='SELL' and macd.macd <=0)) and
   ...> (cps.TimeFrame = 'HOUR' or cps.TimeFrame='FOUR_HOURS')
   ...> group by cps.TimeFrame, IsPriceTargetMet;
FOUR_HOURS|0|579
FOUR_HOURS|1|831
HOUR|0|2402
HOUR|1|2727
sqlite>
sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> cps.TimeOfSignal > '2022-03-01' and
   ...> ((cps.TradeType='BUY' and macd.macd >=0) or
   ...> (cps.TradeType='SELL' and macd.macd <=0)) and
   ...> (cps.TimeFrame = 'HOUR' or cps.TimeFrame='FOUR_HOURS')
   ...> group by cps.TimeFrame, IsPriceTargetMet;

FOUR_HOURS|0|305
FOUR_HOURS|1|303
HOUR|0|1164
HOUR|1|1150
sqlite>
sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> cps.TimeOfSignal > '2022-03-20' and
   ...> ((cps.TradeType='BUY' and macd.macd >=0) or
   ...> (cps.TradeType='SELL' and macd.macd <=0)) and
   ...> (cps.TimeFrame = 'HOUR' or cps.TimeFrame='FOUR_HOURS')
   ...> group by cps.TimeFrame, IsPriceTargetMet;

FOUR_HOURS|0|17
FOUR_HOURS|1|123
HOUR|0|283
HOUR|1|363
sqlite>
sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> cps.TimeOfSignal > '2022-03-20' and
   ...> ((cps.TradeType='BUY' and macd.macd >=0) or
   ...> (cps.TradeType='SELL' and macd.macd <=0)) and
   ...> (cps.TimeFrame = 'HOUR' or cps.TimeFrame='FOUR_HOURS' or cps.TimeFrame='FIFTEEN_MINUTES')
   ...> group by cps.TimeFrame, IsPriceTargetMet;
FIFTEEN_MINUTES|0|1888
FIFTEEN_MINUTES|1|1741
FOUR_HOURS|0|17
FOUR_HOURS|1|123
HOUR|0|283
HOUR|1|363
sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> ((cps.TradeType='BUY' and macd.macd >=0) or
   ...> (cps.TradeType='SELL' and macd.macd <=0)) and
   ...> (cps.TimeFrame = 'HOUR' or cps.TimeFrame='FOUR_HOURS' or cps.TimeFrame='FIFTEEN_MINUTES')
   ...> group by cps.TimeFrame, IsPriceTargetMet;
FIFTEEN_MINUTES|0|9813
FIFTEEN_MINUTES|1|9575
FOUR_HOURS|0|579
FOUR_HOURS|1|831
HOUR|0|2402
HOUR|1|2727
sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> ((cps.TradeType='BUY' and (macd.macd >=0 or macd.HistogramTrendType='ACCELERATING')) or
   ...> (cps.TradeType='SELL' and (macd.macd <=0 and macd.HistogramTrendType='DECELERATING')))
   ...> group by cps.TimeFrame, IsPriceTargetMet;
DAY|0|176
DAY|1|96
FIFTEEN_MINUTES|0|11732
FIFTEEN_MINUTES|1|10553
FOUR_HOURS|0|813
FOUR_HOURS|1|966
HOUR|0|2984
HOUR|1|3142
sqlite> Select cps.TimeFrame, IsPriceTargetMet, count(0)
   ...> from ChartPatternSignal cps, MACDData macd
   ...>  where cps.TimeOfSignal=macd.Time and cps.TimeFrame = macd.TimeFrame and cps.CoinPair = replace(macd.CoinPair, '_', '') and
   ...> ProfitPotentialPercent > 0 and IsPriceTargetMet is not null and
   ...> ProfitPercentAtSignalTargetTime is not null and ProfitPercentAtSignalTargetTime <100 and Attempt = 1 and
   ...> ((cps.TradeType='BUY' and macd.macd >=0) or
   ...> (cps.TradeType='SELL' and macd.macd <=0))
   ...> group by cps.TimeFrame, IsPriceTargetMet;

DAY|0|54
DAY|1|40
FIFTEEN_MINUTES|0|9813
FIFTEEN_MINUTES|1|9575
FOUR_HOURS|0|579
FOUR_HOURS|1|831
HOUR|0|2402
HOUR|1|2727
sqlite>
sqlite>
