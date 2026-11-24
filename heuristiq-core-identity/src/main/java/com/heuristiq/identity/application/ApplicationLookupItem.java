package com.heuristiq.uam.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.heuristiq.uam.enums.UamEnums.ApplicationType;

import io.swagger.v3.oas.annotations.media.Schema;

/** One active application of the session organization, as other screens show it (pickers, labels). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "An active application of the session organization.")
public record ApplicationLookupItem(
        @Schema(example = "CENTRALISED_WALLET_APP") String code,
        @Schema(example = "Centralised Wallet") String name,
        ApplicationType type,
        Integer displayOrder,
        String description,
        @Schema(example = "account_balance_wallet") String iconHint) {
}
