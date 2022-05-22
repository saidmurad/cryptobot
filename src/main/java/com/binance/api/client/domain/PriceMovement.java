package com.binance.api.client.domain;

import com.binance.api.client.domain.market.CandlestickInterval;

public class PriceMovement {
    private String symbol;
    private CandlestickInterval interval;
    private PriceMovementDirection movementDirection;
    private double level;
    private double nextLevel;

    public PriceMovement(String symbol, CandlestickInterval interval, PriceMovementDirection movementDirection) {
        this.symbol = symbol;
        this.interval = interval;
        this.movementDirection = movementDirection;
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

    public PriceMovementDirection getMovementDirection() {
        return movementDirection;
    }

    public void setMovementDirection(PriceMovementDirection movementDirection) {
        this.movementDirection = movementDirection;
    }

    public double getLevel() {
        return level;
    }

    public void setLevel(double level) {
        this.level = level;
    }

    public double getNextLevel() {
        return nextLevel;
    }

    public void setNextLevel(double nextLevel) {
        this.nextLevel = nextLevel;
    }

    @Override
    public String toString() {
        return "TradingSignalOutput{" +
                "symbol='" + symbol + '\'' +
                ", interval=" + interval +
                ", eventType=" + movementDirection +
                ", level=" + level +
                ", nextLevel=" + nextLevel +
                '}';
    }
}
