package com.heuristq.dinein.image;

import com.heuristq.dinein.shared.web.ApiPaths;

/** Relative URLs for image bytes; the SPA and Nginx serve everything from one origin. */
public final class ImageUrls {

    private ImageUrls() {
    }

    public static String full(Long imageId) {
        return imageId == null ? null : ApiPaths.V1 + "/images/" + imageId;
    }

    public static String thumb(Long imageId) {
        return imageId == null ? null : ApiPaths.V1 + "/images/" + imageId + "/thumb";
    }
}
