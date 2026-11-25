package com.heuristiq.identity.helper;

import com.heuristiq.identity.enums.UamEnums.LanguageEnum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionalPreference {

    private LanguageEnum language;

    private String timezone;

    private String currency;

    private String dateFormat;

    private String timeFormat;

    private String numberFormat;

    private String firstDayOfWeek;
}
