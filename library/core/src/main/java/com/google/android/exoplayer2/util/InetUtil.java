package com.google.android.exoplayer2.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InetUtil {
    private static final Pattern regexIpv4 = Pattern.compile("(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})",
            Pattern.CASE_INSENSITIVE);

    public static boolean isPrivateIpAddress(String ipAddress) {
        Matcher matcher = regexIpv4.matcher(ipAddress);
        if (matcher.find()) {
            String[] ip = matcher.group(0).split("\\.");
            short[] ipNumber = new short[] {
                    Short.parseShort(ip[0]),
                    Short.parseShort(ip[1]),
                    Short.parseShort(ip[2]),
                    Short.parseShort(ip[3])
            };

            boolean is24BitBlock = ipNumber[0] == 10;
            if (is24BitBlock) return true; // Return to prevent further processing

            boolean is20BitBlock = ipNumber[0] == 172 && ipNumber[1] >= 16 && ipNumber[1] <= 31;
            if (is20BitBlock) return true; // Return to prevent further processing

            boolean is16BitBlock = ipNumber[0] == 192 && ipNumber[1] == 168;
            if (is16BitBlock) return true; // Return to prevent further processing

            boolean isLinkLocalAddress = ipNumber[0] == 169 && ipNumber[1] == 254;
            return isLinkLocalAddress;
        }

        return false;
    }
}
