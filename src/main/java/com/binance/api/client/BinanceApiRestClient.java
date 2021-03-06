package com.binance.api.client;

import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.*;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.Asset;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.api.client.domain.market.BookTicker;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;

import java.util.List;

/**
 * Binance API facade, supporting synchronous/blocking access Binance's REST API.
 */
public interface BinanceApiRestClient {

  // General endpoints

  /**
   * Test connectivity to the Rest API.
   */
  void ping() throws BinanceApiException;

  /**
   * Test connectivity to the Rest API and get the current server time.
   *
   * @return current server time.
   */
  Long getServerTime() throws BinanceApiException;

  /**
   * @return Current exchange trading rules and symbol information
   */
  ExchangeInfo getExchangeInfo() throws BinanceApiException;

  /**
   * @return All the supported assets and whether or not they can be withdrawn.
   */
  List<Asset> getAllAssets() throws BinanceApiException;

  // Market Data endpoints

  /**
   * Get order book of a symbol.
   *
   * @param symbol ticker symbol (e.g. ETHBTC)
   * @param limit depth of the order book (max 100)
   */
  OrderBook getOrderBook(String symbol, Integer limit) throws BinanceApiException;

  /**
   * Get recent trades (up to last 500). Weight: 1
   *
   * @param symbol ticker symbol (e.g. ETHBTC)
   * @param limit of last trades (Default 500; max 1000.)
   */
  List<TradeHistoryItem> getTrades(String symbol, Integer limit) throws BinanceApiException;

  /**
   * Get older trades. Weight: 5
   *
   * @param symbol ticker symbol (e.g. ETHBTC)
   * @param limit of last trades (Default 500; max 1000.)
   * @param fromId TradeId to fetch from. Default gets most recent trades.
   */
  List<TradeHistoryItem> getHistoricalTrades(String symbol, Integer limit, Long fromId) throws BinanceApiException;

  /**
   * Get compressed, aggregate trades. Trades that fill at the time, from the same order, with
   * the same price will have the quantity aggregated.
   *
   * If both <code>startTime</code> and <code>endTime</code> are sent, <code>limit</code>should not
   * be sent AND the distance between <code>startTime</code> and <code>endTime</code> must be less than 24 hours.
   *
   * @param symbol symbol to aggregate (mandatory)
   * @param fromId ID to get aggregate trades from INCLUSIVE (optional)
   * @param limit Default 500; max 1000 (optional)
   * @param startTime Timestamp in ms to get aggregate trades from INCLUSIVE (optional).
   * @param endTime Timestamp in ms to get aggregate trades until INCLUSIVE (optional).
   * @return a list of aggregate trades for the given symbol
   */
  List<AggTrade> getAggTrades(String symbol, String fromId, Integer limit, Long startTime, Long endTime) throws BinanceApiException;

  /**
   * Return the most recent aggregate trades for <code>symbol</code>
   *
   * @see #getAggTrades(String, String, Integer, Long, Long)
   */
  List<AggTrade> getAggTrades(String symbol) throws BinanceApiException;

  /**
   * Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
   *
   * @param symbol symbol to aggregate (mandatory)
   * @param interval candlestick interval (mandatory)
   * @param limit Default 500; max 1000 (optional)
   * @param startTime Timestamp in ms to get candlestick bars from INCLUSIVE (optional).
   * @param endTime Timestamp in ms to get candlestick bars until INCLUSIVE (optional).
   * @return a candlestick bar for the given symbol and interval
   */
  List<Candlestick> getCandlestickBars(String symbol, CandlestickInterval interval, Integer limit, Long startTime, Long endTime) throws BinanceApiException;

  /**
   * Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
   * @param symbol symbol to aggregate (mandatory)
   * @param interval candlestick interval (mandatory)
   * @param limit Default 500; max 1000 (optional)
   * @return a candlestick bar for the given symbol and interval
   * @throws BinanceApiException binance exeption
   */
  List<Candlestick> getCandlestickBars(String symbol, CandlestickInterval interval, Integer limit) throws BinanceApiException;

  /**
   * Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
   *
   * @see #getCandlestickBars(String, CandlestickInterval, Integer, Long, Long)
   */
  List<Candlestick> getCandlestickBars(String symbol, CandlestickInterval interval) throws BinanceApiException;

  /**
   * Get 24 hour price change statistics.
   *
   * @param symbol ticker symbol (e.g. ETHBTC)
   */
  TickerStatistics get24HrPriceStatistics(String symbol) throws BinanceApiException;

  /**
   * Get 24 hour price change statistics for all symbols.
   */
  List<TickerStatistics> getAll24HrPriceStatistics() throws BinanceApiException;

  /**
   * Get Latest price for all symbols.
   */
  List<TickerPrice> getAllPrices() throws BinanceApiException;

  /**
   * Get latest price for <code>symbol</code>.
   *
   * @param symbol ticker symbol (e.g. ETHBTC)
   */
  TickerPrice getPrice(String symbol) throws BinanceApiException;

  /**
   * Get best price/qty on the order book for all symbols.
   */
  List<BookTicker> getBookTickers() throws BinanceApiException;

  // Account endpoints

  /**
   * Send in a new order.
   *
   * @param order the new order to submit.
   * @return a response containing details about the newly placed order.
   */
  NewOrderResponse newOrder(NewOrder order) throws BinanceApiException;

  /**
   * Test new order creation and signature/recvWindow long. Creates and validates a new order but does not send it into the matching engine.
   *
   * @param order the new TEST order to submit.
   */
  void newOrderTest(NewOrder order) throws BinanceApiException;

  /**
   * Check an order's status.
   * @param orderStatusRequest order status request options/filters
   *
   * @return an order
   */
  Order getOrderStatus(OrderStatusRequest orderStatusRequest) throws BinanceApiException;

  /**
   * Cancel an active order.
   *
   * @param cancelOrderRequest order status request parameters
   */
  CancelOrderResponse cancelOrder(CancelOrderRequest cancelOrderRequest) throws BinanceApiException;

  /**
   * Get all open orders on a symbol.
   *
   * @param orderRequest order request parameters
   * @return a list of all account open orders on a symbol.
   */
  List<Order> getOpenOrders(OrderRequest orderRequest) throws BinanceApiException;

  /**
   * Get all account orders; active, canceled, or filled.
   *
   * @param orderRequest order request parameters
   * @return a list of all account orders
   */
  List<Order> getAllOrders(AllOrdersRequest orderRequest) throws BinanceApiException;

  /**
   * Send in a new OCO;
   *
   * @param oco
   *            the OCO to submit
   * @return a response containing details about the newly placed OCO.
   */
  NewOCOResponse newOCO(NewOCO oco) throws BinanceApiException;

  /**
   * Cancel an entire Order List
   *
   * @return
   */
  CancelOrderListResponse cancelOrderList(CancelOrderListRequest cancelOrderListRequest) throws BinanceApiException;

  /**
   * Check an order list status
   *
   * @param orderListStatusRequest
   * @return an orderList
   */
  OrderList getOrderListStatus(OrderListStatusRequest orderListStatusRequest) throws BinanceApiException;

  /**
   * Get all list os orders
   *
   * @param allOrderListRequest
   * @return
   */
  List<OrderList> getAllOrderList(AllOrderListRequest allOrderListRequest) throws BinanceApiException;

  /**
   * Get current account information.
   */
  Account getAccount(Long recvWindow, Long timestamp) throws BinanceApiException;

  /**
   * Get current account information using default parameters.
   */
  Account getAccount() throws BinanceApiException;

  /**
   * Get trades for a specific account and symbol.
   *
   * @param symbol symbol to get trades from
   * @param limit default 500; max 1000
   * @param fromId TradeId to fetch from. Default gets most recent trades.
   * @return a list of trades
   */
  List<Trade> getMyTrades(String symbol, Integer limit, Long fromId, Long recvWindow, Long timestamp) throws BinanceApiException;

  /**
   * Get trades for a specific account and symbol.
   *
   * @param symbol symbol to get trades from
   * @param limit default 500; max 1000
   * @return a list of trades
   */
  List<Trade> getMyTrades(String symbol, Integer limit) throws BinanceApiException;

  /**
   * Get trades for a specific account and symbol.
   *
   * @param symbol symbol to get trades from
   * @return a list of trades
   */
  List<Trade> getMyTrades(String symbol) throws BinanceApiException;
  
  List<Trade> getMyTrades(String symbol, Long fromId) throws BinanceApiException;

  /**
   * Submit a withdraw request.
   *
   * Enable Withdrawals option has to be active in the API settings.
   *
   * @param asset asset symbol to withdraw
   * @param address address to withdraw to
   * @param amount amount to withdraw
   * @param name description/alias of the address
   * @param addressTag Secondary address identifier for coins like XRP,XMR etc.
   */
  WithdrawResult withdraw(String asset, String address, String amount, String name, String addressTag) throws BinanceApiException;

  /**
   * Conver a list of assets to BNB
   * @param asset the list of assets to convert
   */
  DustTransferResponse dustTranfer(List<String> asset) throws BinanceApiException;

  /**
   * Fetch account deposit history.
   *
   * @return deposit history, containing a list of deposits
   */
  DepositHistory getDepositHistory(String asset) throws BinanceApiException;

  /**
   * Fetch account withdraw history.
   *
   * @return withdraw history, containing a list of withdrawals
   */
  WithdrawHistory getWithdrawHistory(String asset) throws BinanceApiException;

  /**
   * Fetch sub-account transfer history.
   *
   * @return sub-account transfers
   */
  List<SubAccountTransfer> getSubAccountTransfers() throws BinanceApiException;

  /**
   * Fetch deposit address.
   *
   * @return deposit address for a given asset.
   */
  DepositAddress getDepositAddress(String asset) throws BinanceApiException;

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
   * Close out a new user data stream.
   *
   * @param listenKey listen key that identifies a data stream
   */
  void closeUserDataStream(String listenKey) throws BinanceApiException;
}
