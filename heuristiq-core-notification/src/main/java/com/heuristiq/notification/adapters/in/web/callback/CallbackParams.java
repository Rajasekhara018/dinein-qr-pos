package com.railcab.nms.adapters.in.web.callback;

import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

/** Reads provider callback parameters whose name varies between providers or API versions. */
public final class CallbackParams {

    private CallbackParams() {
    }

    /** Value of the first of {@code names} that has text in {@code params}; null when none does. */
    public static String firstPresent(Map<String, String> params, List<String> names) {
        for (String name : names) {
            String v = params.get(name);
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
