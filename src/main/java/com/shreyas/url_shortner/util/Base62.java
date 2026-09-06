package com.shreyas.url_shortner.util;

public class Base62 {

    private static final String CHARACTERS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SHORT_CODE_LENGTH = 7;

    public static String encode(long number) {

        if (number < 0)
            throw new IllegalArgumentException("Number must be 0 or greater");

        StringBuilder sb = new StringBuilder();

        if (number == 0) {
            sb.append('0');
        }

        while (number > 0) {

            int remainder = (int)(number % 62);

            sb.append(CHARACTERS.charAt(remainder));

            number /= 62;
        }

        while (sb.length() < SHORT_CODE_LENGTH) {
            sb.append('0');
        }

        return sb.reverse().toString();
    }
}
