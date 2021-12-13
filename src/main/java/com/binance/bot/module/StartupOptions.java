package com.binance.bot.module;

import com.google.devtools.common.options.Option;
        import com.google.devtools.common.options.OptionsBase;

        import java.util.List;

/**
 * Command-line options
 */
public class StartupOptions extends OptionsBase {

    @Option(
            name = "use_testnet",
            abbrev = 't',
            help = "Whether to connect to the Binance Test Vision using Test API keys",
            defaultValue = "true"
    )
    public boolean useTestNet;

    @Option(
            name = "api_key",
            abbrev = 'k',
            help = "Binance Api Key",
            defaultValue = "31MUPiM1hMNKt4uUXJ8et0GbYwXYvZ33HzLmRdXYEtrvvM8A0p59N510EvQcA99A"
    )
    public String apiKey;

    @Option(
            name = "api_secret",
            abbrev = 's',
            help = "Binance Api Secret",
            defaultValue = "SVI8zkiPBsM96Uyd1UijdojGpIefAT9ZzB5pWPSB9M6uPYSUHGyK32O6Bxdxppzf"
    )
    public String apiSecret;
}