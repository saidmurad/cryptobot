package com.binance.bot.finance;

import com.binance.api.client.BinanceApiRestClient;

public class ManageMoney {
    private final BinanceApiRestClient binanceApiRestClient;

    public ManageMoney(BinanceApiRestClient binanceApiRestClient) {
        this.binanceApiRestClient = binanceApiRestClient;
    }

    /*********** Available balances ***************************/
    // Returns the USDT amount in the Spot account.
    public double getSpotAccountUSDTBalance() {
        return 0;
    }

    // Returns a Coin amount in the Spot account, for example "ETH".
    public double getSpotAccountUSDTBalance(String coin) {
        return 0;
    }


    // Returns the USDT amount available to trade in the USM-Futures account.
    public double getUSDMFuturesUSDTBalance() {
        return 0;
    }

    /**
     * Moves the specified amount of USDT from Spot to the margin pair "Coin"-USDT, for example if
     * the coin argument is ETH, the USDT would be moved from Spot to ETH_USDT margin account.
     * @return
     */
    public boolean moveUSDTSpotToMargin(String coin, double numUSDT) {
        return false;
    }

    /**
     * Moves specified number of a coin from Spot to Margin account. For example if coin is "ETH", the
     * target account would be the ETH-USDT margin account.
     */
    public boolean moveCoinSpotToMargin(String coin, double numCoins) {
        return false;
    }

    /**
     * In a margin account trading pair, borrows a specified amount of USDT. For example, in an
     * ETH-USDT margin account with 1000 USDT already present, the method may be called to borrow
     * 3000 USDT, in order to buy ETH on margin.
     */
    public boolean borrowUSDTMargin(double numUSDT) {
        return false;
    }

    /**
     * In a margin account trading pair, borrows a specified amount of Coin. For example, in an
     * ETH-USDT margin account with 1000 USDT already present, the method may be called to borrow
     * 5 ETH in order to short sell ETH on margin.
     */
    public boolean borrowCoinMargin(String coin, double numCoins) {
        return false;
    }

    /**
     * Returns any borrowed USDT and borrowed coins in a Margin account, and moves both USDT and
     * coins back to Spot account. This method does not execute any trades.
     * @throws InsufficientFundsException if there is not sufficient funds to return.
     */
    public boolean defundMarginAccount(String coin) {
        return false;
    }

    /**
     * Moves the specified amount of USDT from Spot to USD-M Futures account.
     * @return success or failure.
     */
    public boolean moveUSDTSpotToUSDMFutures(double numUSDT) {
        return false;
    }

    /** Reverse of moveUSDTSpotToUSDMFutures */
    public boolean returnUSDTUSDMFuturesToSpot(double numUSDT) {
        return false;
    }

    /**
     * Moves the specified amount of a coin from Spot to Coin-M Futures account.
     * @param Name of the coin, such as BTC/ETH.
     * @return success or failure.
     */
    public boolean moveCoinsSpotToCoinMFutures(String coin, double numCoins) {
        return false;
    }

    /** Reverse of moveCoinsSpotToCoinMFutures, moves all coins back to Spot. */
    public boolean returnCoinsCoinMFuturesToSpot(String coin) {
        return false;
    }
}
