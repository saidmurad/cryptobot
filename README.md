Change log for important changes:
06/02/2022 04:50 pm: Made disappeared and reappeared signals to be included immediately as a new chart pattern signal to be processed.
06/02/2022 07:38 pm: Fixed the bug in chart pattern invalidation logic due to symbols temporarily not trading on binance.
08/02/2022 02:25 pm: Attempt count as a primary key in the table and Profit percent inserted 
at the time of signal invalidation. Removed backlog_from_coldstart as a reason for signal 
invalidation. 

Next steps:
0. What causes the duplicate trades in the same list so often?
1. Include dtime of signal in the where clause when querying for signals to trade.
2. Set Profit percent at time of signal invalidation for the time period from
06/02/2022 07:38 pm to 08/02/2022 02:25 pm
3. Symbol may not be in trading stage at the time of signal arrival and at the time of signal 
invalidation. Need to handle it.
For the time period the above fix is not there, I also need a lagging task to back fill those prices.
4. In AltfinPatternsReader set ten candlestick time no more than the target time.
5. Things to check before placing a trade:
    HOw far the real price is away from the price at the time of signal (may be already over it)
        2z32gtbbhand from the price target.
    May be ignore isInsertedLate for trading decsion as comeback signals don't have that info, and
        also ok to place trade if large percent of the target price is yet to be met.
6. Trading status of the symbol.
7. Signal for the same symbol getting replaced with a different trade in the same trade type
- no need to kill the trade here.
7. Add an error message column to the table and put failed actions there.
8. BinanceapiException to be made a checked exception.d
9. At signal target time, a market order similar to the profit taking order but is realizing loss or lesser profits than
predicted, and should set signal status and position status to exited.
10. Add gap between stop loss and limit order.
11. whenver starting the program need to sart the market price ticker steram for the active positions held.
12. Filter out DAY timeframe.