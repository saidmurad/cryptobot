package com.binance.common;

import java.util.ArrayList;
import java.util.List;

public class TestUtil {
    public static String[] getArgs() {
        List<String> args = new ArrayList<>();
        String arg = System.getProperty("--use_testnet");
        if (arg != null) {
            args.add("--use_testnet=" + arg);
        }
        arg = System.getProperty("--api_key");
        if (arg != null) {
            args.add("--api_key=" + arg);
        }
        arg = System.getProperty("--api_secret");
        if (arg != null) {
            args.add("--api_secret=" + arg);
        }
        String[] argsArr = new String[args.size()];
        args.toArray(argsArr);
        return argsArr;
    }
}
