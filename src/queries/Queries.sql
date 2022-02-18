1) Question: How good is the timing of the signal invalidation?
select ProfitPercentAtTimeOfSignalInvalidation from ChartPatternSignal where Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55') and MaxLoss is not null order by ProfitPercentAtTimeOfSignalInvalidation desc;
Answer: Max loss was 5.4% at time of signal Invalidation.

2) Question: How many knee jerk signal invalidations, i.e. for signals that did reach their price targets but invalidation occurred
before that time.
select count(*) from ChartPatternSignal where Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=1 and ProfitPotentialPercent>0
and DateTime(TimeOfSignalInvalidation) < DateTime(PriceTargetMetTime);
Answer: 199 out of 457. Full list in 'Damage Output.sql'

select TimeFrame,count(0) from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=1 and DateTime(TimeOfSignalInvalidation)<DateTime(PriceTargetMetTime) group by TimeFrame;
FIFTEEN_MINUTES|181
HOUR|18

3a) Profit percent vs Loss percent summation using invalidation signals or with stop loss for the same time period.
Sum (profit percents) i would get for the (subset of ) signals meeting their target:

select sum(s) from
(select Sum(ProfitPotentialPercent) as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)>=DateTime(PriceTargetMetTime) and IsPriceTargetMet=1
UNION ALL
select Sum(ProfitPercentAtTimeOfSignalInvalidation) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)<DateTime(PriceTargetMetTime)
 and IsPriceTargetMet=1
UNION ALL
select Sum(ProfitPercentAtTimeOfSignalInvalidation) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
 and IsPriceTargetMet=0);

662

In terms of meeting their price targets the patterns behave WORSE than CHANCE but not if making use of the Invalidation signal:
select TimeFrame, sum(s) from
(select TimeFrame, Sum(ProfitPotentialPercent) as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)>=DateTime(PriceTargetMetTime) and IsPriceTargetMet=1
group by TimeFrame
UNION ALL
select TimeFrame,Sum(ProfitPercentAtTimeOfSignalInvalidation) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)<DateTime(PriceTargetMetTime)
 and IsPriceTargetMet=1
 group by TimeFrame
UNION ALL
select TimeFrame,Sum(ProfitPercentAtTimeOfSignalInvalidation) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
 and IsPriceTargetMet=0
group by TimeFrame)
group by TimeFrame;

DAY|-5.66208513238653
FIFTEEN_MINUTES|728.365468946208
FOUR_HOURS|274.171616499249
HOUR|109.068764472354



Breakdown by num trades per hour for above query.
select Date(TimeOfSignal), HourOfDay, TimeFrame, IsPriceTargetMet, count(0) from (
select Date(TimeOfSignal) as TimeOfSignal,
        cast(24*(julianDay(TimeOfSignal)-julianDay(strftime('%Y-%m-%d 00:00:00', TimeOfSignal))) as Integer)
        as HourOfDay,
        TimeFrame, IsPriceTargetMet
from ChartPatternSignal where Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55'))
group by Date(TimeOfSignal), HourOfDay, TimeFrame, IsPriceTargetMet;

Above query with avg ProfitPercents per hour - shows same side trades after big movements result in huge losses. most important query

select Date(TimeOfSignal), HourOfDay, TimeFrame, TradeType, count(0) as NumTrades, Avg (s) as avgProfitPercent, Sum(s) as sumProfitPercents from (
select TimeOfSignal, cast(24*(julianDay(TimeOfSignal)-julianDay(strftime('%Y-%m-%d 00:00:00', TimeOfSignal))) as Integer)
                             as HourOfDay, TimeFrame, TradeType, s from (
select TimeOfSignal as TimeOfSignal, TimeFrame, TradeType, ProfitPotentialPercent as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)>=DateTime(PriceTargetMetTime) and IsPriceTargetMet=1
UNION ALL
select TimeOfSignal as TimeOfSignal, TimeFrame, TradeType, ProfitPercentAtTimeOfSignalInvalidation as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)<DateTime(PriceTargetMetTime)
 and IsPriceTargetMet=1
UNION ALL
select TimeOfSignal as TimeOfSignal,TimeFrame,  TradeType, ProfitPercentAtTimeOfSignalInvalidation as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
 and IsPriceTargetMet=0))
 group by Date(TimeOfSignal), HourOfDay, TimeFrame, TradeType
 order by Date(TimeOfSignal), HourOfDay, TimeFrame, TradeType;

Worst output was these rows:
2022-02-10|13|FIFTEEN_MINUTES|BUY|6|-1.53830541745894|-9.22983250475363
2022-02-10|13|FIFTEEN_MINUTES|SELL|152|-0.418829184239808|-63.6620360044509
2022-02-10|14|FIFTEEN_MINUTES|SELL|110|-1.42172947335218|-156.39024206874
2022-02-10|14|HOUR|SELL|220|-0.815697340813212|-179.453414978907

What trades where getting issued in that hour that caused the havoc:
select TimeOfSignal, TradeType, IsPriceTargetMet, MaxLossPercent from ChartPatternSignal
where Date(TimeOfSignal)=Date('2022-02-16')
and cast(24*(julianDay(TimeOfSignal)-julianDay(strftime('%Y-%m-%d 00:00:00', TimeOfSignal))) as Integer)=4
and TimeFrame='FIFTEEN_MINUTES' order by DateTime(TimeOfSignal);

Price drop above 2.5% in BTC in a 15 minute candle https://screenshot.googleplex.com/3FygLNYtVK2vQqJ

Above query by day:
select date(TimeOfSignal), sum(s) from
(select date(TimeOfSignal) as TimeOfSignal, ProfitPotentialPercent as s from ChartPatternSignal
where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)>=DateTime(PriceTargetMetTime) and IsPriceTargetMet=1
UNION ALL
select date(TimeOfSignal) as TimeOfSignal, ProfitPercentAtTimeOfSignalInvalidation as s from ChartPatternSignal
where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)<DateTime(PriceTargetMetTime) and IsPriceTargetMet=1
UNION ALL
select date(TimeOfSignal), ProfitPercentAtTimeOfSignalInvalidation as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
 and IsPriceTargetMet=0)
 group by date(TimeOfSignal);

Above query by day, timeframe:
select date(TimeOfSignal), TimeFrame, sum(s) from
(select date(TimeOfSignal) as TimeOfSignal, TimeFrame, Sum(ProfitPotentialPercent) as s from ChartPatternSignal
where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)>=DateTime(PriceTargetMetTime) and IsPriceTargetMet=1
group by date(TimeOfSignal), TimeFrame

UNION ALL
select date(TimeOfSignal) as TimeOfSignal, TimeFrame, Sum(ProfitPercentAtTimeOfSignalInvalidation) as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation)<DateTime(PriceTargetMetTime) and IsPriceTargetMet=1
group by date(TimeOfSignal), TimeFrame

UNION ALL
select date(TimeOfSignal), TimeFrame, Sum(ProfitPercentAtTimeOfSignalInvalidation) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
 and IsPriceTargetMet=0
 group by date(TimeOfSignal), TimeFrame)
 group by date(TimeOfSignal), TimeFrame;


3b) Sum (profit percents) without considering invalidation time (but for the same time period as above).

with 5% Stop loss:
select sum(s) from
(select ProfitPotentialPercent as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=1 and MaxLossPercent < 5
UNION ALL
select ProfitPercentAtTimeOfSignalInvalidation as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=0 and MaxLossPercent < 5
UNION ALL
select -5 as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=0 and MaxLossPercent >= 5)
358

with 10% Stop loss:
select sum(s) from
(select ProfitPotentialPercent as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=1 and MaxLossPercent < 10
UNION ALL
select ProfitPercentAtTimeOfSignalInvalidation as s from ChartPatternSignal where
ProfitPotentialPercent>0 and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=0 and MaxLossPercent < 10
UNION ALL
select -10 as s from ChartPatternSignal where ProfitPotentialPercent>0
and Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and IsPriceTargetMet=0 and MaxLossPercent >= 10)
454
-------------------------------------------------------------------
4) Profit vs Loss overall entire time period assuming stop loss of 5% (signal invalidation was not there in place for the majority time):
select sum(ProfitPotentialPercent) as s from ChartPatternSignal where IsPriceTargetMet=1;
// Loss queries: a. Where the trade did not get stopped out at 5%.
select sum(ProfitPercentAtSignalTargetTime) as s from ChartPatternSignal where IsPriceTargetMet=0 and MaxLossPercent<5;
select sum(-5) as s from ChartPatternSignal where IsPriceTargetMet=0 and MaxLossPercent>5;

UNION ALLed overall with 5% stop loss

select sum(s) from (select sum(ProfitPotentialPercent) as s from ChartPatternSignal
where IsPriceTargetMet=1 and ProfitPotentialPercent>0 and MaxLossPercent<5
UNION ALL
select sum(-5) as s from ChartPatternSignal
where IsPriceTargetMet=1 and MaxLossPercent>5 and ProfitPotentialPercent>0
UNION ALL
select sum(ProfitPercentAtSignalTargetTime) as s from ChartPatternSignal
where IsPriceTargetMet=0 and MaxLossPercent<5 and ProfitPotentialPercent>0
UNION ALL
select sum(-5) as s from ChartPatternSignal
where IsPriceTargetMet=0 and MaxLossPercent>5 and ProfitPotentialPercent>0);
-306

UNION ALLed overall with 8% stop loss
select sum(s) from (select sum(ProfitPotentialPercent) as s from ChartPatternSignal
where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent<8
UNION ALL
select sum(-8) as s from ChartPatternSignal
where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent >= 8
UNION ALL
select sum(ProfitPercentAtSignalTargetTime) as s from ChartPatternSignal
where ProfitPotentialPercent>0 and IsPriceTargetMet=0
and MaxLossPercent<8
UNION ALL
select sum(-8) as s from ChartPatternSignal
where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent>8);
1930

UNION ALLed overall with 10% stop loss
select sum(s) from (
select sum(ProfitPotentialPercent) as s from ChartPatternSignal where ProfitPotentialPercent>0
and IsPriceTargetMet=1 and MaxLossPercent<10
UNION ALL
select sum(-10) as s from ChartPatternSignal where ProfitPotentialPercent>0
and IsPriceTargetMet=1 and MaxLossPercent >= 10
UNION ALL
select sum(-10) as s from ChartPatternSignal where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent>10
UNION ALL
select sum(ProfitPercentAtSignalTargetTime) as s from ChartPatternSignal where ProfitPotentialPercent>0 and IsPriceTargetMet=0
and MaxLossPercent<10
UNION ALL
select sum(-10) as s from ChartPatternSignal where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent >10);
320

UNION ALLed overall with 20% stop loss
select sum(s) from (select sum(ProfitPotentialPercent) as s from ChartPatternSignal where ProfitPotentialPercent>0
and IsPriceTargetMet=1 and MaxLossPercent<20
UNION ALL
select sum(-20) as s from ChartPatternSignal where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent>=20
UNION ALL
select sum(ProfitPercentAtSignalTargetTime) as s from ChartPatternSignal where ProfitPotentialPercent>0 and IsPriceTargetMet=0
and MaxLossPercent<20
UNION ALL
select sum(-20) as s from ChartPatternSignal where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent >= 20);
3375

For a particular day
select Date(TimeOfSignal), sum(s) from (
   select Date(TimeOfSignal) as TimeOfSignal, ProfitPotentialPercent as s from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent<5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, -5 as s from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent >= 5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, ProfitPercentAtSignalTargetTime as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent<5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, -5 as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent>5
   )
   where Date(TimeOfSignal) = Date('2022-02-15')
group by Date(TimeOfSignal)
order by Date(TimeOfSignal);

UNION ALLed overall without stop loss
select sum(s) from (select sum(ProfitPotentialPercent) as s from ChartPatternSignal where IsPriceTargetMet=1
UNION ALL
select sum(ProfitPercentAtSignalTargetTime) as s from ChartPatternSignal where IsPriceTargetMet=0);
-255

UNION ALLed and grouped by TradeType.
select TradeType, sum(s) from (
   select TradeType, sum(ProfitPotentialPercent) as s from ChartPatternSignal where IsPriceTargetMet=1 group by TradeType
   UNION ALL
   select TradeType, sum(ProfitPercentAtSignalTargetTime) as s from ChartPatternSignal where IsPriceTargetMet=0 and MaxLossPercent<5 group by TradeType
   UNION ALL
   select TradeType, sum(-5) as s from ChartPatternSignal where IsPriceTargetMet=0 and MaxLossPercent>5 group by TradeType)
group by TradeType;
BUY|5023.55634601381
SELL|1756.94388844238


UNION ALLed and grouped by Date.
select Date(TimeOfSignal), count(0) as numTrades, sum(s) as sumProfitPercents from (
   select Date(TimeOfSignal) as TimeOfSignal, ProfitPotentialPercent as s from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent<5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, -5 as s from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent >= 5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal,  ProfitPercentAtSignalTargetTime as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent<5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, -5 as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent>=5)
group by Date(TimeOfSignal)
order by Date(TimeOfSignal);

Above query with additional grouping by TimeFrame:
select Date(TimeOfSignal), TimeFrame, count(0) as numTrades, sum(s) as sumProfitPercents from (
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame as TimeFrame, ProfitPotentialPercent as s from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent<5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame as TimeFrame, -5 as s from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent >= 5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame as TimeFrame, ProfitPercentAtSignalTargetTime as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent<5
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame as TimeFrame, -5 as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent>5)
group by Date(TimeOfSignal), TimeFrame
order by Date(TimeOfSignal), TimeFrame;

Grouped by Date, Timeframe, trade type.
select Date(TimeOfSignal), TimeFrame, TradeType, sum(s) from (
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame, TradeType, sum(ProfitPotentialPercent) as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent<5
   group by Date(TimeOfSignal), TimeFrame, TradeType
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame, TradeType, sum(-5) as s
      from ChartPatternSignal
      where ProfitPotentialPercent>0 and IsPriceTargetMet=1 and MaxLossPercent>=5
       group by Date(TimeOfSignal), TimeFrame, TradeType
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame, TradeType, sum(ProfitPercentAtSignalTargetTime) as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent<5
   group by Date(TimeOfSignal), TimeFrame, TradeType
   UNION ALL
   select Date(TimeOfSignal) as TimeOfSignal, TimeFrame, TradeType, sum(-5) as s
   from ChartPatternSignal
   where ProfitPotentialPercent>0 and IsPriceTargetMet=0 and MaxLossPercent>5
   group by Date(TimeOfSignal), TimeFrame, TradeType)
group by Date(TimeOfSignal), TimeFrame, TradeType
order by Date(TimeOfSignal), TimeFrame, TradeType;

Max loss percent by date:
select Date(TimeOfSignal), Max(MaxLossPercent)
from ChartPatternSignal
group by Date(TimeOfSignal)
order by Date(TimeOfSignal)

---------------------------
5) If using Ten candlestick as the exit time.
select sum(s) from (
select sum(ProfitPotentialPercent) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and IsPriceTargetMet=1 and DateTime(PriceTargetMetTime) <= DateTime(TenCandlestickTime) and DateTime(MaxLossTime) > DateTime(PriceTargetMetTime) and MaxLossPercent<5
UNION ALL
select sum(ProfitPercentAtTenCandlestickTime) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and DateTime(MaxLossTime) > DateTime(TenCandlestickTime) and MaxLossPercent<5
UNION ALL
select sum(-5) as s from ChartPatternSignal where
ProfitPotentialPercent>0 and MaxLossPercent >= 5 and MaxLossTime <= DateTime(TenCandlestickTime));

-2454.

Does invalidation occur at 10 candlestick time?
select count(*) from ChartPatternSignal where Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation) <= Datetime(TenCandlestickTime);
2437

select TimeFrame, count(*) from ChartPatternSignal where Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation) <= Datetime(TenCandlestickTime)
group by TimeFrame;

select count(*) from ChartPatternSignal where Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55')
and DateTime(TimeOfSignalInvalidation) > Datetime(TenCandlestickTime);
2

select TimeFrame, (JulianDay(TimeOfSignalInvalidation) - JulianDay(TimeOfSignal)) * 24
from ChartPatternSignal
where Datetime(TimeOfSignal)>=DateTime('2022-02-08 08:55') and TimeOfSignalInvalidation is not null
order by TimeFrame, (JulianDay(TimeOfSignalInvalidation) - JulianDay(TimeOfSignal)) * 24;
