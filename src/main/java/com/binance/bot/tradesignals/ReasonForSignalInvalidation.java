package com.binance.bot.tradesignals;

public enum ReasonForSignalInvalidation {
  REMOVED_FROM_SOURCESIGNALS,
  SYMBOL_NOT_SUPPORTED,
  EXPIRED,
  FAKEOUT,
  BACKLOG_AND_COLD_START, VOLUME_TOO_LOW
}
