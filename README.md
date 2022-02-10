Change log for important changes:
06/02/2022 04:50 pm: Made disappeared and reappeared signals to be included immediately as a new chart pattern signal to be processed.
06/02/2022 07:38 pm: Fixed the bug in chart pattern invalidation logic due to symbols temporarily not trading on binance.
08/02/2022 02:25 pm: Attempt count as a primary key in the table and Profit percent inserted 
at the time of signal invalidation. Removed backlog_from_coldstart as a reason for signal 
invalidation. 

Next steps:
1. Set Profit percent at time of signal invalidation for the time period from
06/02/2022 07:38 pm to 08/02/2022 02:25 pm
2. Symbol may not be in trading stage at the time of signal arrival and at the time of signal 
invalidation. Need to handle it.
For the time period the above fix is not there, I also need a lagging task to back fill those prices.
3. In AltfinPatternsReader set ten candlestick time no more than the target time.
4. Things to check before placing a trade:
    HOw far the real price is away from the price at the time of signal (may be already over it)
        2z32gtbbhand from the price target.
    May be ignore isInsertedLate for trading decsion as comeback signals don't have that info, and
        also ok to place trade if large percent of the target price is yet to be met.
5. Trading status of the symbol.