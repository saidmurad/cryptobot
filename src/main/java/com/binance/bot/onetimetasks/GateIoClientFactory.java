package com.gateiobot;

import io.gate.gateapi.ApiClient;
import io.gate.gateapi.api.MarginApi;
import io.gate.gateapi.api.SpotApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GateIoClientFactory {
  @Value("${api_key}")
  private String apiKey;

  @Value("${api_secret}")
  private String apiSecret;
  private final ApiClient client;

  @Autowired
  public GateIoClientFactory() {
    client = new ApiClient();
    client.setApiKeySecret(apiKey, apiSecret);
  }

  public SpotApi getSpotApi() {
    return new SpotApi(client);
  }

  public MarginApi getMarginApi() {
    return new MarginApi(client);
  }
}
