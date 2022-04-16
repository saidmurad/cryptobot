package com.binance.api.client;

import com.binance.api.client.domain.TransferType;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;

import java.util.List;

public interface BinanceApiMarginRestClient {
    /**
     * Get current margin account information using default parameters.
     */
    MarginAccount getAccount() throws BinanceApiException;

    /**
     * Get all open orders on margin account for a symbol.
     *
     * @param orderRequest order request parameters
     */
    List<Order> getOpenOrders(OrderRequest orderRequest) throws BinanceApiException;

    /**
     * Send in a new margin order.
     *
     * @param order the new order to submit.
     * @return a response containing details about the newly placed order.
     */
    MarginNewOrderResponse newOrder(MarginNewOrder order) throws BinanceApiException;

    /**
     * Cancel an active margin order.
     *
     * @param cancelOrderRequest order status request parameters
     */
    CancelOrderResponse cancelOrder(CancelOrderRequest cancelOrderRequest) throws BinanceApiException;

    /**
     * Check margin order's status.
     * @param orderStatusRequest order status request options/filters
     *
     * @return an order
     */
    Order getOrderStatus(OrderStatusRequest orderStatusRequest) throws BinanceApiException;

    /**
     * Get margin trades for a specific symbol and an order.
     *
     * @param symbol symbol to get trades from
     * @param orderId order id to get the trade fills for.
     * @return a list of trades
     */
    List<Trade> getMyTrades(String symbol, Long orderId) throws BinanceApiException;

    // User stream endpoints

    /**
     * Start a new user data stream.
     *
     * @return a listen key that can be used with data streams
     */
    String startUserDataStream() throws BinanceApiException;

    /**
     * PING a user data stream to prevent a time out.
     *
     * @param listenKey listen key that identifies a data stream
     */
    void keepAliveUserDataStream(String listenKey) throws BinanceApiException;

    /**
     * Execute transfer between spot account and margin account
     * @param asset asset to repay
     * @param amount amount to repay
     * @return transaction id
     */
    MarginTransaction transfer(String asset, String amount, TransferType type) throws BinanceApiException;

    /**
     * Apply for a loan
     * @param asset asset to repay
     * @param amount amount to repay
     * @return transaction id
     */
    MarginTransaction borrow(String asset, String amount) throws BinanceApiException;

    /**
     * Query loan record
     * @param asset asset to query
     * @return repay records
     */
    RepayQueryResult queryRepay(String asset, long startTime) throws BinanceApiException;

    /**
     * Query max borrowable
     * @param asset asset to query
     * @return max borrowable
     */
    MaxBorrowableQueryResult queryMaxBorrowable(String asset) throws BinanceApiException;

    /**
     * Query loan record
     * @param asset asset to query
     * @param txId the tranId in POST /sapi/v1/margin/repay
     * @return loan records
     */
    RepayQueryResult queryRepay(String asset, String txId) throws BinanceApiException;

    /**
     * Repay loan for margin account
     * @param asset asset to repay
     * @param amount amount to repay
     * @return transaction id
     */
    MarginTransaction repay(String asset, String amount) throws BinanceApiException;

    /**
     * Query loan record
     * @param asset asset to query
     * @param txId the tranId in POST /sapi/v1/margin/loan
     * @return loan records
     */
    LoanQueryResult queryLoan(String asset, String txId) throws BinanceApiException;

    List<CrossMarginPair> getCrossMarginCurrencyPairs() throws BinanceApiException;
}
