package com.heuristiq.identity.helper;

import java.util.Set;
import java.util.regex.Pattern;

import com.heuristiq.identity.common.UamException;

/**
 * Checks the free-text UI choices a user sends before they are stored, so a client cannot save text the web apps would later
 * put into a page attribute. Unknown values are refused (400), not silently dropped.
 */
public final class UiPreferenceRules {

    /** The text sizes the web apps offer. */
    public static final Set<String> FONT_SCALES = Set.of("small", "normal", "large", "xlarge");

    private static final Pattern THEME_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    private UiPreferenceRules() {
    }

    /** The preference with a blank theme or text size cleared; a theme id or text size outside the allowed form is refused. */
    public static UiPreference checked(UiPreference ui) {
        if (ui == null) {
            return null;
        }
        String theme = blankToNull(ui.getThemeId());
        String scale = blankToNull(ui.getFontScale());
        if (theme != null && !THEME_ID.matcher(theme).matches()) {
            throw new UamException("The theme is not valid");
        }
        if (scale != null && !FONT_SCALES.contains(scale)) {
            throw new UamException("The text size must be one of " + String.join(", ", FONT_SCALES.stream().sorted().toList()));
        }
        ui.setThemeId(theme);
        ui.setFontScale(scale);
        return ui;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
