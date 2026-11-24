package com.heuristiq.uam.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Framework-neutral pagination result for use-case interfaces -- unlike {@link Page}, this
 * carries no Spring Data dependency, so a *UseCase port doesn't leak an infrastructure type into
 * its contract. Field names/shape intentionally mirror Spring Data's {@code Page} JSON output
 * (content/totalElements/totalPages/size/number/numberOfElements/first/last/empty) so existing
 * API consumers see no wire-format change; {@link #from(Page)} is the only place that couples
 * back to Spring Data, used by service implementations to adapt a repository's {@code Page}
 * before returning it through the use-case boundary.
 */
@Schema(description = "Framework-neutral pagination result mirroring Spring Data Page's JSON shape.")
public record PageResult<T>(
        @Schema(description = "The page's items.") List<T> content,
        @Schema(description = "Total number of items across all pages.", example = "137") long totalElements,
        @Schema(description = "Total number of pages.", example = "3") int totalPages,
        @Schema(description = "Requested page size.", example = "50") int size,
        @Schema(description = "Zero-based page number.", example = "0") int number,
        @Schema(description = "Number of items actually present on this page.") int numberOfElements,
        @Schema(description = "Whether this is the first page.") boolean first,
        @Schema(description = "Whether this is the last page.") boolean last,
        @Schema(description = "Whether this page has no items.") boolean empty
) {
    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber(),
                page.getNumberOfElements(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty());
    }
}
