package com.binance.bot.tradesignals;

public enum ReasonForSignalInvalidation {
  REMOVED_FROM_ALTFINS,
  SYMBOL_NOT_SUPPORTED,
  EXPIRED,
  FAKEOUT,
  VOLUME_TOO_LOW
}
