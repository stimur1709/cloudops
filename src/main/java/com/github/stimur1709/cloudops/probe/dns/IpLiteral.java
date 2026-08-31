package com.github.stimur1709.cloudops.probe.dns;

import java.util.regex.Pattern;

public final class IpLiteral {

    private static final Pattern IPV4 =
            Pattern.compile("(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}");

    private IpLiteral() {}

    public static boolean isIpLiteral(String value) {
        return value.indexOf(':') >= 0 || IPV4.matcher(value).matches();
    }
}
