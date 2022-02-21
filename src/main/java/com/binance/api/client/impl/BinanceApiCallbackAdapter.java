package com.binance.api.client.impl;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiError;
import com.binance.api.client.exception.BinanceApiException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;

import static com.binance.api.client.impl.BinanceApiServiceGenerator.getBinanceApiError;

/**
 * An adapter/wrapper which transforms a Callback from Retrofit into a BinanceApiCallback which is exposed to the client.
 */
public class BinanceApiCallbackAdapter<T> implements Callback<T> {

  private final BinanceApiCallback<T> callback;

  public BinanceApiCallbackAdapter(BinanceApiCallback<T> callback) {
    this.callback = callback;
  }

  public void onResponse(Call<T> call, Response<T> response) {
    if (response.isSuccessful()) {
      try {
        callback.onResponse(response.body());
      } catch (BinanceApiException e) {
        e.printStackTrace();
      }
    } else {
      if (response.code() == 504) {
        // HTTP 504 return code is used when the API successfully sent the message but not get a response within the timeout period.
        // It is important to NOT treat this as a failure; the execution status is UNKNOWN and could have been a success.
        return;
      }
      try {
        BinanceApiError apiError = getBinanceApiError(response);
        onFailure(call, new BinanceApiException(apiError));
      } catch (IOException | BinanceApiException e) {
        onFailure(call, new BinanceApiException(e));
      }
    }
  }

  @Override
  public void onFailure(Call<T> call, Throwable throwable) {
    if (throwable instanceof BinanceApiException) {
      try {
        callback.onFailure(throwable);
      } catch (BinanceApiException e) {
        throw new RuntimeException(e);
      }
    } else {
      try {
        callback.onFailure(new BinanceApiException(throwable));
      } catch (BinanceApiException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
