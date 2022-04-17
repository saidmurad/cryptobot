package com.gateiobot;

import io.gate.gateapi.ApiClient;
import io.gate.gateapi.api.MarginApi;
import io.gate.gateapi.api.SpotApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GateIoClientFactory {
  @Value("${gate_io_api_key}")
  private String apiKey;

  @Value("${gate_io_api_secret}")
  private String apiSecret;
  private final ApiClient client;

  @Autowired
  public GateIoClientFactory() {
    client = new ApiClient();
    apiKey = "a084ec5f27b406fa5adea8acdb4f8639";
    apiSecret = "67102023e99c22b02be00b68144de72cbdb1567ae18186a82bfc27265cefbbce";
    client.setApiKeySecret(apiKey, apiSecret);
  }

  public SpotApi getSpotApi() {
    return new SpotApi(client);
  }

  public MarginApi getMarginApi() {
    return new MarginApi(client);
  }
}
