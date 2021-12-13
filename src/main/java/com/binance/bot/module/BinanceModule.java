package com.binance.bot.module;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.config.BinanceApiConfig;
import com.binance.bot.module.annotations.IsTestNet;
import com.google.devtools.common.options.OptionsParser;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.inject.Singleton;

public class BinanceModule extends AbstractModule {

    private final StartupOptions startupOptions;

    public BinanceModule(String args[]) {
        OptionsParser parser = OptionsParser.newOptionsParser(StartupOptions.class);
        parser.parseAndExitUponError(args);
        startupOptions = parser.getOptions(StartupOptions.class);
        BinanceApiConfig.useTestnet = startupOptions.useTestNet;
    }

    @Provides
    @IsTestNet
    public boolean providesIsUseTestNet() {
        return startupOptions.useTestNet;
    }

    @Provides
    @Singleton
    public BinanceApiClientFactory provideBinanceApiClientFactory(){
        return BinanceApiClientFactory.newInstance(startupOptions.apiKey, startupOptions.apiSecret, startupOptions.useTestNet, startupOptions.useTestNet);
    }

    @Provides
    @Singleton
    /**
     * Based on the settings in settings.conf provides a Binance Rest Api client.
     */
    public BinanceApiRestClient provideBinanceApiRestClient(BinanceApiClientFactory factory) {
        return factory.newRestClient();
    }
}
