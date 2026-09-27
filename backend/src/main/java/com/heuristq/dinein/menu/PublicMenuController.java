package com.heuristq.dinein.menu;

import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/menu")
public class PublicMenuController {

    private final PublicMenuService publicMenuService;

    public PublicMenuController(PublicMenuService publicMenuService) {
        this.publicMenuService = publicMenuService;
    }

    /** Revalidated on every load (no-cache) but cheap thanks to the menu-version ETag. */
    @GetMapping
    public ResponseEntity<MenuResponse> menu(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        PublicMenuService.CachedMenu cached = publicMenuService.getMenu();
        if (ifNoneMatch != null && ifNoneMatch.contains(cached.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(cached.etag())
                    .cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(cached.etag()).cacheControl(CacheControl.noCache()).body(cached.menu());
    }
}
