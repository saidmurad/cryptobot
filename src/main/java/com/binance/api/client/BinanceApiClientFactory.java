package com.binance.api.client;

import com.binance.api.client.impl.*;
import com.binance.api.client.config.BinanceApiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static com.binance.api.client.impl.BinanceApiServiceGenerator.getSharedClient;

/**
 * A factory for creating BinanceApi client objects.
 */
@Component
public class BinanceApiClientFactory {

  /**
   * API Key
   */
  @Value("${api_key}")
  private String apiKey;

  /**
   * Secret.B
   */
  @Value("${api_secret}")
  private String apiSecret;
  @Value("${api_key}")
  private String marginAcctApiKey;
      @Value("${api_secret}")
  private String marginAcctApiSecret;

  public BinanceApiClientFactory() {}

  @Autowired
  public BinanceApiClientFactory(@Value("${use_testnet}") boolean useTestnet, @Value("${api_key}") String apiKey, @Value("${api_secret}") String apiSecret) {
    construct(apiKey, apiSecret, useTestnet, useTestnet);
  }

  /**
   * Instantiates a new binance api client factory.
   *
   * @param apiKey the API key
   * @param apiSecret the Secret
   * @param useTestnet true if endpoint is spot test network URL; false if endpoint is production spot API URL.
   * @param useTestnetStreaming true for spot test network websocket streaming; false for no streaming.
   */
  private BinanceApiClientFactory(String apiKey, String apiSecret, boolean useTestnet, boolean useTestnetStreaming) {
      construct(apiKey, apiSecret, useTestnet, useTestnetStreaming);
  }

  private void construct(String apiKey, String secret, boolean useTestnet, boolean useTestnetStreaming) {
    this.apiKey = apiKey;
    this.apiSecret = secret;
    if (useTestnet) {
      BinanceApiConfig.useTestnet = true;
      BinanceApiConfig.useTestnetStreaming = useTestnetStreaming;
    }
  }
  /**
   * New instance.
   *
   * @param apiKey the API key
   * @param secret the Secret
   *
   * @return the binance api client factory
   */
  public static BinanceApiClientFactory newInstance(String apiKey, String secret) {
    return new BinanceApiClientFactory(apiKey, secret, false, false);
  }

  /**
   * New instance with optional Spot Test Network endpoint.
   *
   * @param apiKey the API key
   * @param secret the Secret
   * @param useTestnet true if endpoint is spot test network URL; false if endpoint is production spot API URL.
   * @param useTestnetStreaming true for spot test network websocket streaming; false for no streaming.
   *
   * @return the binance api client factory.
   */
    public static BinanceApiClientFactory newInstance(String apiKey, String secret, boolean useTestnet, boolean useTestnetStreaming) {
      return new BinanceApiClientFactory(apiKey, secret, useTestnet, useTestnetStreaming);
  }

  /**
   * New instance without authentication.
   *
   * @return the binance api client factory
   */
  public static BinanceApiClientFactory newInstance() {
    return new BinanceApiClientFactory(null, null, false, false);
  }

  /**
   * New instance without authentication and with optional Spot Test Network endpoint.
   *
   * @param useTestnet true if endpoint is spot test network URL; false if endpoint is production spot API URL.
   * @param useTestnetStreaming true for spot test network websocket streaming; false for no streaming.
   *
   * @return the binance api client factory.
   */
  public static BinanceApiClientFactory newInstance(boolean useTestnet, boolean useTestnetStreaming) {
    return new BinanceApiClientFactory(null, null, useTestnet, useTestnetStreaming);
  }

  /**
   * Creates a new synchronous/blocking REST client.
   */
  public BinanceApiRestClient newRestClient() {
    return new BinanceApiRestClientImpl(apiKey, apiSecret);
  }

  /**
   * Creates a new asynchronous/non-blocking REST client.
   */
  public BinanceApiAsyncRestClient newAsyncRestClient() {
    return new BinanceApiAsyncRestClientImpl(apiKey, apiSecret);
  }

  /**
   * Creates a new asynchronous/non-blocking Margin REST client.
   */
  public BinanceApiAsyncMarginRestClient newAsyncMarginRestClient() {
    return new BinanceApiAsyncMarginRestClientImpl(marginAcctApiKey, marginAcctApiSecret);
  }

  /**
   * Creates a new synchronous/blocking Margin REST client.
   */
  public BinanceApiMarginRestClient newMarginRestClient() {
    return new BinanceApiMarginRestClientImpl(marginAcctApiKey, marginAcctApiSecret);
  }

  /**
   * Creates a new web socket client used for handling data streams.
   */
  public BinanceApiWebSocketClient newWebSocketClient() {
    return new BinanceApiWebSocketClientImpl(getSharedClient());
  }

  /**
   * Creates a new synchronous/blocking Swap REST client.
   */
  public BinanceApiSwapRestClient newSwapRestClient() {
    return new BinanceApiSwapRestClientImpl(apiKey, apiSecret);
  }

  /**
   * Creates a new synchronous/blocking Futures REST client.
   */
  /*public SyncRequestClient newFuturesRestClient() {
    RequestOptions requestOptions = new RequestOptions();
    if (useTestnet) {
      requestOptions.setUrl(FUTURES_TESTNET_URL);
    }
    return SyncRequestClient.create(futuresApiKey, futuresApiSecret, requestOptions);
  }*/
}
