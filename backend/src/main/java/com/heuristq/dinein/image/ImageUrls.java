package com.heuristq.dinein.image;

/** Relative URLs for image bytes; the SPA and Nginx serve everything from one origin. */
public final class ImageUrls {

    private ImageUrls() {
    }

    public static String full(Long imageId) {
        return imageId == null ? null : "/api/images/" + imageId;
    }

    public static String thumb(Long imageId) {
        return imageId == null ? null : "/api/images/" + imageId + "/thumb";
    }
}
