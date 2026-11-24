package com.heuristiq.uam.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessibilityPreference {

    private Boolean highContrast;

    private Boolean screenReader;

    private Double fontScale;

    private Boolean reducedMotion;
}
