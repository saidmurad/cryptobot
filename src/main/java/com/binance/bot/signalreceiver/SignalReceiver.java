package com.binance.bot.signalreceiver;

import com.binance.bot.processsignals.ProcessSignals;

import javax.inject.Inject;

/**
 * Socket receiver for Chart signal patterns.
 * The data received over the socket will be a serialized byte format of the proto array ChartPatternSignal[].
 * All signals in the array will be of a particular timeframe only.
 * Code should deserialize the proto array and call ProcessSignals.
 */
public class SignalReceiver {
  private final ProcessSignals processSignals;

  @Inject
  SignalReceiver(ProcessSignals processSignals) {
    this.processSignals = processSignals;
  }

  // Each Socket message is an array: ChartPattern[]
}
