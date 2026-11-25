package com.heuristiq.identity.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.common.UamException;

class UiPreferenceRulesTest {

    private static UiPreference ui(String themeId, String fontScale) {
        UiPreference ui = new UiPreference();
        ui.setThemeId(themeId);
        ui.setFontScale(fontScale);
        return ui;
    }

    @Test
    void anAbsentPreferenceStaysAbsent() {
        assertThat(UiPreferenceRules.checked(null)).isNull();
    }

    @Test
    void everyOfferedTextSizeAndAThemeIdAreKept() {
        for (String size : UiPreferenceRules.FONT_SCALES) {
            assertThat(UiPreferenceRules.checked(ui("pasupu-dark", size)).getFontScale()).isEqualTo(size);
        }
        assertThat(UiPreferenceRules.checked(ui(" ocean ", "normal")).getThemeId()).isEqualTo("ocean");
    }

    @Test
    void blankChoicesAreClearedSoTheDeploymentDefaultApplies() {
        UiPreference cleared = UiPreferenceRules.checked(ui("  ", ""));

        assertThat(cleared.getThemeId()).isNull();
        assertThat(cleared.getFontScale()).isNull();
    }

    @Test
    void aTextSizeOrThemeOutsideTheAllowedFormIsRefused() {
        assertThatThrownBy(() -> UiPreferenceRules.checked(ui(null, "huge"))).isInstanceOf(UamException.class)
                .hasMessageContaining("text size must be one of large, normal, small, xlarge");
        assertThatThrownBy(() -> UiPreferenceRules.checked(ui("<script>", "normal"))).isInstanceOf(UamException.class)
                .hasMessageContaining("theme is not valid");
        assertThatThrownBy(() -> UiPreferenceRules.checked(ui("a".repeat(41), null))).isInstanceOf(UamException.class);
    }
}
