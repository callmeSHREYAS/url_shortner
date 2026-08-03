package com.shreyas.url_shortner.util;

public class Base62 {

    private static final String CHARACTERS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static String encode(long number) {

        if (number == 0)
            return "0";

        StringBuilder sb = new StringBuilder();

        while (number > 0) {

            int remainder = (int)(number % 62);

            sb.append(CHARACTERS.charAt(remainder));

            number /= 62;
        }

        return sb.reverse().toString();
    }
}
