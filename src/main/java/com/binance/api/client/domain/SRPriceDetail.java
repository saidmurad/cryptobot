package com.binance.api.client.domain;

import com.binance.api.client.domain.market.CandlestickInterval;

import java.util.List;
import java.util.stream.Collectors;

public class SRPriceDetail {
    private String symbol;
    private CandlestickInterval interval;
    private List<Float> bounds;

    public SRPriceDetail(String symbol, CandlestickInterval interval, List<Float> bounds) {
        this.symbol = symbol;
        this.interval = interval;
        this.bounds = bounds.stream().sorted().collect(Collectors.toList());
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public CandlestickInterval getInterval() {
        return interval;
    }

    public void setInterval(CandlestickInterval interval) {
        this.interval = interval;
    }

    public List<Float> getBounds() {
        return bounds;
    }

    public void setBounds(List<Float> bounds) {
        this.bounds = bounds;
    }
}
