package com.binance.bot.trading;

import com.binance.api.client.domain.account.MarginNewOrderResponse;
import com.binance.api.client.domain.account.Trade;
import com.binance.bot.common.Util;
import com.binance.bot.tradesignals.TradeType;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

class TradeFillData {
   private double qty;
   private double avgPrice;
   private double commissionUSDT;

  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

   TradeFillData(MarginNewOrderResponse sellOrderResponse, TradeType tradeType, double currPrice)
       throws ParseException {
    List<Trade> fills = sellOrderResponse.getFills();
    double weightedSum=0;
    for (Trade fill: fills) {
      double fillPrice = Util.getDoubleValue(fill.getPrice());
      double fillQty = Util.getDoubleValue(fill.getQty());
      if (tradeType == TradeType.BUY) {
        double commissionInAsset = numberFormat.parse(fill.getCommission()).doubleValue();
        fillQty -= commissionInAsset;
        commissionUSDT += commissionInAsset * currPrice;
      } else {
        double commissionInUSDT = numberFormat.parse(fill.getCommission()).doubleValue();
        commissionUSDT += commissionInUSDT;
      }
      weightedSum += fillPrice * fillQty;
      qty += fillQty;
    }
    avgPrice = weightedSum / qty;
  }

  double getQuantity() {
     return qty;
  }

  double getAvgPrice() {
     return avgPrice;
  }

  double getCommissionUSDT() {
     return commissionUSDT;
  }
}
