package com.heuristiq.uam.helper;

import com.heuristiq.uam.enums.UamEnums.ThemeEnum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiPreference {

    private ThemeEnum theme;

    private String accentColor;

    private Boolean compactMode;

    private Boolean animationsEnabled;

    private Integer pageSize;

    private Boolean showSidebar;

    private Boolean darkMode;

    private String landingPage;

    /** The web theme the user chose (for example {@code ocean} or {@code pasupu-dark}); null follows the deployment's default. */
    private String themeId;

    /** The text size the user chose: small, normal, large or xlarge; null is normal. */
    private String fontScale;
}
