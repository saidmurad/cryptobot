                            `IsSignalOn
1. Disappear from Altfins       ->0
2. Reapper in Altfins           ->1
3. For trade placement          1->
4. Target time just reached     -
5. setSignalTargetTimePrice     ->0
6. Exit market order            ->0

How signal re-occurences are handled as I'm contemplating making the altfin invalidations always get 
recorded in DB.
if ignore altfin invalidations for trading:
appear     signal attempt 1 (IsSignalOn=1) : Place Trade
disappear  signal attempt 1 (IsSignalOn=0) . Trade not exited
reappear   attempt 1 (IsSignalOn=0) attempt 2 (IsSignalOn=1): Need to avoid placing trade again.
                                                                Can include attempt=1 in WHERE clause.

if consider altfin invalidations for trading:
appear     signal attempt 1 (IsSignalOn=1) : Place Trade
disappear  signal attempt 1 (IsSignalOn=0) . Trade exited
reappear   attempt 1 (IsSignalOn=0) attempt 2 (IsSignalOn=1): Trade placed for attempt 2. 