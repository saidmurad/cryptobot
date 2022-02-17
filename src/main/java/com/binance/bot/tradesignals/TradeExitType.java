package com.binance.bot.tradesignals;

public enum TradeExitType {
  TARGET_TIME_PASSED,

  REMOVED_FROM_ALTFINS,

  PROFIT_TARGET_MET,

  STOP_LOSS,

  ORDERED_TO_EXIT_POSITIONS;
}
