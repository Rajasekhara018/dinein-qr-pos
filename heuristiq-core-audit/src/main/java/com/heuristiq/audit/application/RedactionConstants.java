package com.heuristiq.audit.application;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mask text, key vocabularies and patterns of the header and body redaction.
 */
public final class RedactionConstants {

    private RedactionConstants() {
    }

    /** Replacement for a redacted value. */
    public static final String MASK = "***";

    public static final Pattern JWT_LIKE = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}(\\.[A-Za-z0-9_-]*)?");

    public static final Pattern BEARER_LIKE = Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}");

    /** key = value pairs in JSON-ish or key=value text; the value may be an unterminated quoted string. */
    public static final Pattern PAIR = Pattern.compile(
            "([\"']?)([A-Za-z0-9_.-]{1,64})\\1(\\s*[:=]\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"?|'(?:[^'\\\\]|\\\\.)*'?|[^,&}\\]\\s\"']+)");

    public static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    public static final Set<String> SECRET_EXACT = Set.of("password", "passwd", "pwd", "pin", "mpin", "upipin", "otp",
            "token", "accesstoken", "refreshtoken", "idtoken", "secret", "clientsecret", "apikey", "cvv", "cvv2", "cvc",
            "authorization", "signature", "passcode", "privatekey", "sessionid", "cookie");

    public static final Set<String> CARD_ACCOUNT = Set.of("cardnumber", "cardno", "accountnumber", "accountno",
            "bankaccount", "bankaccountnumber", "debitcardnumber", "creditcardnumber");

    public static final Set<String> CONTACT = Set.of("phone", "phonenumber", "mobile", "mobilenumber", "msisdn",
            "email", "emailid", "emailaddress", "recipient");

    public static final Set<String> VPA = Set.of("vpa", "upi", "upiid", "upiaddress", "payeevpa", "payervpa");

    public static final Pattern SEPARATORS = Pattern.compile("[_.\\-\\s]");

    public static final int KIND_CACHE_MAX = 4096;

    /** Maximum stored length of a header name. */
    public static final int MAX_HEADER_NAME = 100;
    /** Maximum stored length of a header value. */
    public static final int MAX_HEADER_VALUE = 1000;
    /** Mask shown in place of all but the last digits of a phone-like recipient. */
    public static final String MASKED_DIGITS = "******";
    /** A recipient needs at least this many digits to be masked as a number. */
    public static final int MIN_MASKABLE_DIGITS = 6;
    /** Number of trailing digits left visible on a masked number. */
    public static final int VISIBLE_DIGITS = 4;

    /** Shared default list of sensitive path patterns, kept for documentation and tests. */
    public static final List<String> DEFAULT_SKIP_BODY_PATHS = List.of("/**/identity/auth/**", "/**/auth/**",
            "/**/*otp*", "/**/*otp*/**", "/**/*password*", "/**/*password*/**", "/**/*token*", "/**/*token*/**",
            "/**/*card*", "/**/*card*/**");

}
