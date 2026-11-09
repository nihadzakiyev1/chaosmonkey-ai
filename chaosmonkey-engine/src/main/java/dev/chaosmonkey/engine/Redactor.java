package dev.chaosmonkey.engine;

import java.util.regex.Pattern;

public final class Redactor {
    private static final Pattern SECRET = Pattern.compile("(?i)(\"?(?:password|token|api[_-]?key|authorization|cookie|email)\"?\\s*[=:]\\s*)(\"[^\"\\r\\n]*\"|[^,;\\r\\n]+)");
    public static String text(String value) {
        if (value == null) return "";
        return SECRET.matcher(value).replaceAll("$1[REDACTED]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[EMAIL]");
    }
    private Redactor() {}
}
