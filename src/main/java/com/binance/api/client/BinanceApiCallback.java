package com.binance.api.client;

import com.binance.api.client.exception.BinanceApiException;

import java.text.ParseException;

/**
 * BinanceApiCallback is a functional interface used together with the BinanceApiAsyncClient to provide a non-blocking REST client.
 *
 * @param <T> the return type from the callback
 */
@FunctionalInterface
public interface BinanceApiCallback<T> {

    /**
     * Called whenever a response comes back from the Binance API.
     *
     * @param response the expected response object
     */
    void onResponse(T response) throws BinanceApiException;

    /**
     * Called whenever an error occurs.
     *
     * @param cause the cause of the failure
     */
    default void onFailure(Throwable cause) throws BinanceApiException {}
}