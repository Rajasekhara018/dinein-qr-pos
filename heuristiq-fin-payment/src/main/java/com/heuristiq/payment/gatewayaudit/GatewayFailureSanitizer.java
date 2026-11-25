package com.heuristiq.payment.gatewayaudit;

import java.util.regex.Pattern;

/**
 * Turns an exception into a short failure reason that is safe to store and to send to the audit
 * service: bounded, control characters removed, bearer/JWT tokens, secret-looking {@code key=value}
 * pairs, long digit runs (card numbers), e-mail addresses, URL query strings and anything that looks
 * like an embedded JSON/HTML body removed.
 */
public final class GatewayFailureSanitizer {

    private static final String REDACTED = "***";

    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|key|salt|hash|signature|cvv|cvc|authorization|card[_-]?(?:number|no)|pan)"
                    + "([\"']?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,&;}]+)");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern URL_QUERY = Pattern.compile("(https?://[^\\s?\"']+)\\?[^\\s\"']*");
    private static final Pattern BODY_START = Pattern.compile("[{<\\[].*", Pattern.DOTALL);
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}+");

    private GatewayFailureSanitizer() {
    }

    /**
     * A reason of the form {@code ExceptionSimpleName: cleaned message}, at most {@code maxLength}
     * characters; never null.
     */
    public static String reasonFor(Throwable error, int maxLength) {
        String name = error.getClass().getSimpleName();
        String message = clean(error.getMessage(), maxLength);
        String reason = message.isEmpty() ? name : name + ": " + message;
        return truncate(reason, maxLength);
    }

    /** Cleans free text; returns an empty string for null. */
    public static String clean(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String s = BODY_START.matcher(text).replaceFirst("[body omitted]");
        s = BEARER.matcher(s).replaceAll(REDACTED);
        s = JWT.matcher(s).replaceAll(REDACTED);
        s = SECRET_PAIR.matcher(s).replaceAll("$1$2" + REDACTED);
        s = URL_QUERY.matcher(s).replaceAll("$1");
        s = EMAIL.matcher(s).replaceAll("[email]");
        s = DIGIT_RUN.matcher(s).replaceAll("[digits]");
        s = CONTROL.matcher(s).replaceAll(" ").trim();
        return truncate(s, maxLength);
    }

    /** Cuts to {@code maxLength} characters. */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
