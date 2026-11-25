package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.pos.domain.PosAddress;
import io.swagger.v3.oas.annotations.media.Schema;

/** A postal address as sent and returned by the POS endpoints. */
@Schema(description = "A postal address.")
public record PosAddressItem(
        String line1, String line2, String city,
        @Schema(description = "State or region; required in IN and US") String region,
        @Schema(description = "Postal code checked by country") String postalCode,
        @Schema(description = "ISO 3166 alpha-2 country", example = "IN") String country) {

    PosAddress toDomain() {
        return new PosAddress(line1, line2, city, region, postalCode, country);
    }

    static PosAddressItem from(PosAddress a) {
        return a == null ? null : new PosAddressItem(a.line1(), a.line2(), a.city(), a.region(), a.postalCode(), a.country());
    }
}
