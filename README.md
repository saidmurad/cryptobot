Change log for important changes:
06/02/2022 04:50 pm: Made disappeared and reappeared signals to be included immediately as a new chart pattern signal to be processed.
06/02/2022 07:38 pm: Fixed the bug in chart pattern invalidation logic due to symbols temporarily not trading on binance.
08/02/2022 02:25 pm: Attempt count as a primary key in the table and Profit percent inserted 
at the time of signal invalidation. Removed backlog_from_coldstart as a reason for signal 
invalidation. 

Next steps:
0. Price target already reached.
1. Commission eating into usdt proceeds from Long trade closing. 
2. Fake out (wait till atleast 1 candlestick).
3. What causes the duplicate trades in the same list so often?
4. Include dtime of signal in the where clause when querying for signals to trade.
5. Set Profit percent at time of signal invalidation for the time period from
06/02/2022 07:38 pm to 08/02/2022 02:25 pm
6. Symbol may not be in trading stage at the time of signal arrival and at the time of signal 
invalidation. Need to handle it.
For the time period the above fix is not there, I also need a lagging task to back fill those prices.
7. In AltfinPatternsReader set ten candlestick time no more than the target time.
8. Things to check before placing a trade:
    HOw far the real price is away from the price at the time of signal (may be already over it)
        2z32gtbbhand from the price target.
    May be ignore isInsertedLate for trading decsion as comeback signals don't have that info, and
        also ok to place trade if large percent of the target price is yet to be met.
9. Trading status of the symbol.
10. Signal for the same symbol getting replaced with a different trade in the same trade type
- no need to kill the trade here.
7. Add an error message column to the table and put failed actions there.
8. BinanceapiException to be made a checked exception.d
9. At signal target time, a market order similar to the profit taking order but is realizing loss or lesser profits than
predicted, and should set signal status and position status to exited.
10. Add gap between stop loss and limit order.
11. whenver starting the program need to sart the market price ticker steram for the active positions held.
12. Filter out DAY timeframe.

Next Steps for ProfitPercentageCalculation:
1. By applying trade exiton fakeouts to price going reverse beyond the start price in the breakout candlestick.

Note on unused db columns presently:

alter statements pending:
isInsertedLate no longer used.
2. priceAtTimeOfSignalReal is no longe rused as the entery marekt order will give that price.
2. exit columns for stop loss replace previous limit order column ExitLimitOrderId, not used now. need to drop unused cols for limit exit.
3. Fro exit market order single set ofcolumsn ExitOrderId etc, not separate market order tracking for Profit taking and
   for time elapsed exit, not using the term ExitMarketorder because in the case of GateIo it is always a limit order.
