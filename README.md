Change log for important changes:
06/02/2022 04:50 pm: Made disappeared and reappeared signals to be included immediately as a new chart pattern signal to be processed.
06/02/2022 07:38 pm: Fixed the bug in chart pattern invalidation logic due to symbols temporarily not trading on binance.
08/02/2022 12:39 pm: Attempt count as a primary key in the table and Profit percent inserted 
at the time of signal invalidation. Removed backlog_from_coldstart as a reason for signal 
invalidation. 

Next steps:
1. Set Profit percent at time of signal invalidation for the time period from
06/02/2022 07:38 pm to 08/02/2022 12:39 pm
2. Symbol may not be in trading stage at the time of signal invalidation. Need to handle it.
For the time period the above fix is not there, I also need a lagging task to back fill those prices.
3. 