package com.binance.bot.tradesignals;

public enum TradeExitType {
  TARGET_TIME_PASSED,

  REMOVED_FROM_SOURCESIGNALS,

  PROFIT_TARGET_MET,

  STOP_LOSS,

  ORDERED_TO_EXIT_POSITIONS;
}
