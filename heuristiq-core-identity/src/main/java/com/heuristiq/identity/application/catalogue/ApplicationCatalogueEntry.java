package com.heuristiq.identity.application.catalogue;

import com.heuristiq.identity.enums.UamEnums.ApplicationType;

/**
 * One application the platform knows about. An organization may only create applications whose code is in the
 * {@link ApplicationCatalogue}; the other values are the defaults used when the administrator does not send them.
 *
 * @param code         the fixed application code (never changes once an application is created)
 * @param name         default display name
 * @param description  default description
 * @param type         the application's type, fixed by the catalogue
 * @param displayOrder default sort position (lower first)
 * @param iconHint     a generic icon name the web apps map to an icon (for example a Material icon name)
 */
public record ApplicationCatalogueEntry(String code, String name, String description, ApplicationType type,
                                        int displayOrder, String iconHint, String privilegePrefix) {
    public ApplicationCatalogueEntry(String code, String name, String description, ApplicationType type,
                                     int displayOrder, String iconHint) {
        this(code, name, description, type, displayOrder, iconHint, null);
    }
}
