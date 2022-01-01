package com.binance.bot.processsignals;

/**
 * Class that schedules waking up every minute and updates the prices in the DB.
 * A Sleep(60000), and doing the processing
 * using the rounded off Clock.millis() to the last minute boundary should work.*
 * Thread should be started from the main program.
 *
 * Further enhancement after completing all other work, for $15 extra.
 * There will be times when the thread was not running due to program error or maintenance. The rows
 * with expiry times that occur during those periods will be left unupdated. To handle this, we can have
 * 2 columns, 1 for 10 candlestick expiry DONE, and another for signal expiry DONE. When the thread
 * starts for the first time, it should query twice for all rows with the current time - 10 candlestick expirty time > 0
 * and not DONE, and again another query for the signal expiry times, and for all the retrieved rows it
 * should query the binance data and update them. This operation should be spawned off as a separate thread
 * since there can be several rows in that state and the regular thread that runs every minute should not
 * be blocked on it.
 */
public class ScheduledProcessing implements Runnable {
  
  @Override
  public void run() {

  }
}
