package com.binance.api.examples;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.MarginNewOrder;
import com.binance.api.client.domain.account.MarginNewOrderResponse;
import com.binance.api.client.domain.account.NewOrderResponseType;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.tradesignals.TradeType;

import java.util.List;

import static com.binance.api.client.domain.account.MarginNewOrder.limitBuy;

/**
 * Examples on how to place orders, cancel them, and query account information.
 */
public class MarginOrdersExample {

    public static void main(String[] args) {
        try {
            BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance("31MUPiM1hMNKt4uUXJ8et0GbYwXYvZ33HzLmRdXYEtrvvM8A0p59N510EvQcA99A", "SVI8zkiPBsM96Uyd1UijdojGpIefAT9ZzB5pWPSB9M6uPYSUHGyK32O6Bxdxppzf", true, true);
            BinanceApiMarginRestClient client = factory.newMarginRestClient();

           /* // Getting list of open orders
            List<Order> openOrders = client.getOpenOrders(new OrderRequest("LINKETH"));
            System.out.println(openOrders);

            // Get status of a particular order
            Order order = client.getOrderStatus(new OrderStatusRequest("LINKETH", 751698L));
            System.out.println(order);

            // Canceling an order
            try {
                CancelOrderResponse cancelOrderResponse = client.cancelOrder(new CancelOrderRequest("LINKETH", 756762l));
                System.out.println(cancelOrderResponse);
            } catch (BinanceApiException e) {
                System.out.println(e.getError().getMsg());
            }

            // Placing a LIMIT order.
            MarginNewOrderResponse newOrderResponse = client.newOrder(limitBuy("LINKUSDT", TimeInForce.GTC, "1000", "0.0001").newOrderRespType(NewOrderResponseType.FULL));*/
            MarginNewOrder marketExitOrder = new MarginNewOrder("BTCUSDT",
                OrderSide.BUY,
                OrderType.MARKET, /* timeInForce= */ null,
            "0.0001");
            MarginNewOrderResponse marketExitOrderResponse = client.newOrder(marketExitOrder);
            System.out.println(marketExitOrderResponse);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}