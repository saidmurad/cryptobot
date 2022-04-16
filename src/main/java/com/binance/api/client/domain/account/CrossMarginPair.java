package com.binance.api.client.domain.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossMarginPair {
  @JsonProperty("symbol")
  private String symbol;
  @JsonProperty("isBuyAllowed")
  private boolean isBuyAllowed;
  @JsonProperty("isSellAllowed")
  private boolean isSellAllowed;
  @JsonProperty("isMarginTrade")
  private boolean isMarginTrade;

  @JsonSetter("symbol")
  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getSymbol() {
    return symbol;
  }

  @JsonSetter("isBuyAllowed")
  public void setIsBuyAllowed(boolean isBuyAllowed) {
    this.isBuyAllowed = isBuyAllowed;
  }

  public boolean getIsBuyAllowed() {
    return isBuyAllowed;
  }

  @JsonSetter("isSellAllowed")
  public void setIsSellAllowed(boolean isSellAllowed) {
    this.isSellAllowed = isSellAllowed;
  }

  public boolean getIsSellAllowed() {
    return isSellAllowed;
  }

  @JsonSetter("isMarginTrade")
  public void setIsMarginTrade(boolean isMarginTrade) {
    this.isMarginTrade = isMarginTrade;
  }

  public boolean getIsMarginTrade() {
    return isMarginTrade;
  }
}
