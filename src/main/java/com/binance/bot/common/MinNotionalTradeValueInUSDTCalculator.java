package com.binance.bot.common;

import com.binance.bot.common.Util;

public class MinNotionalTradeValueInUSDTCalculator {
  /**
   * Calculate the minimum trade value in USDT to deploy for the trade, that allows for the stop loss trade also to be
   * above the minimum notional USDT value ($10) required, otherwise the stop loss order would be rejected for not
   * meeting the minimum notional.
   *
   *    originalTradeUSDTVal * (100 - stopLossPercent) / 100 >= stopLossTradeMinNotionalUSDTVal;
   *    originalTradeUSDTVal >= stopLossTradeMinNotionalUSDTVal * 100 / (100 - stopLossPercent);
   *
   * This function also takes into account the 0.1% commission lost on the entry trade, which will cause the stop limit
   * order quantity to be resulting value after commission. Hence originalTradeUSDTVal should be
   * originalTradeUSDTVal / 0.999 instead to account for the 0.1 % commission loss.
   *
   */
  public static double getMinNotionalTradeValueInUSDTForBuyTrade(
      double minNotionalUSDT, int stepSizeNumDecimalPlaces, double stopLimitPercent, double entryPrice) {
    double minTradeValueUSDT = minNotionalUSDT * 100 / (100 - stopLimitPercent);
    double entryQty = (minTradeValueUSDT / entryPrice) / 0.999;
    // Round up using the step size for the coin pair.
    entryQty = Double.parseDouble(Util.getRoundedUpQuantity(entryQty, stepSizeNumDecimalPlaces));

    // Need to adjust for the truncation that will be applied to the quantity for the stop limit order.
    // Quantity to stop limit for after commissions from the original entry trade.
    double stopLimitOrderQty;
    do {
      stopLimitOrderQty = entryQty * 0.999;
      stopLimitOrderQty = Double.parseDouble(Util.getTruncatedQuantity(stopLimitOrderQty, stepSizeNumDecimalPlaces));
      if (stopLimitOrderQty * entryPrice < minNotionalUSDT) {
        entryQty += bumpQuantityByStepSize(stepSizeNumDecimalPlaces);
      } else {
        break;
      }
    } while (true);
    // Adding extra 25 cents to account for quick price drops when placing order that would reduce the amount being
    // ordered below min notional.
    return (entryQty * entryPrice) + 0.25;
  }

  public static double getMinNotionalTradeValueInUSDTForSellTrade(
      double minNotionalUSDT, int stepSizeNumDecimalPlaces, double entryPrice) {
    double entryQty = (minNotionalUSDT / entryPrice) / 0.999;
    // Round up using the step size for the coin pair.
    // No need to do the bumping unlike needed done for the Buy trades in the above function because, for Sell trades
    // the stop loss is a buy order that already buys slightly more (after applying 0.999) so there is no chance of
    // the stop loss quantity falling belowthe minimum notional.
    return Double.parseDouble(Util.getRoundedUpQuantity(entryQty, stepSizeNumDecimalPlaces));
  }

  private static double bumpQuantityByStepSize(int stepSizeNumDecimalPlaces) {
    double multiplier = Math.pow(10, stepSizeNumDecimalPlaces);
    return 1.0 / multiplier;
  }
}
