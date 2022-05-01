package com.binance.bot.trading;

import com.binance.api.client.domain.account.MarginNewOrderResponse;
import com.binance.api.client.domain.account.Trade;
import com.binance.bot.common.Util;
import com.binance.bot.tradesignals.TradeType;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

public class TradeFillData {
   private double qty;
   private double avgPrice;
   private double commissionUSDT;

  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  // For market order fills.
   public TradeFillData(MarginNewOrderResponse marginNewOrderResponse, TradeType tradeType)
       throws ParseException {
    List<Trade> fills = marginNewOrderResponse.getFills();
    construct(fills, tradeType);
  }

  public TradeFillData(List<Trade> trades, TradeType tradeType) throws ParseException {
     construct(trades, tradeType);
  }

  void construct(List<Trade> trades, TradeType tradeType) throws ParseException {
    double weightedSum=0;
    for (Trade fill: trades) {
      double fillPrice = Util.getDoubleValue(fill.getPrice());
      double fillQty = Util.getDoubleValue(fill.getQty());
      if (tradeType == TradeType.BUY) {
        double commissionInAsset = numberFormat.parse(fill.getCommission()).doubleValue();
        fillQty -= commissionInAsset;
        commissionUSDT += commissionInAsset * fillPrice;
      } else {
        double commissionInUSDT = numberFormat.parse(fill.getCommission()).doubleValue();
        commissionUSDT += commissionInUSDT;
      }
      weightedSum += fillPrice * fillQty;
      qty += fillQty;
    }
    avgPrice = weightedSum / qty;
  }

  public double getQuantity() {
     return qty;
  }

  public double getAvgPrice() {
     return avgPrice;
  }

  public double getCommissionUSDT() {
     return commissionUSDT;
  }
}
