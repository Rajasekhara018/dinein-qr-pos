package com.heuristiq.identity.orgstructure.domain;

import java.util.Set;

/**
 * Which departments a caller may see. Unrestricted callers see everything; a restricted caller sees their own
 * department(s) and everything below ({@code subtree}) and can walk down from the top through the departments above
 * them ({@code visible}, which includes the subtree), but sees people only inside the subtree.
 *
 * @param restricted true when the caller is limited to {@code subtree}
 * @param subtree    the caller's own departments and every department below them
 * @param visible    the subtree plus the departments above it, so the tree can be opened from the top
 */
public record DataScope(boolean restricted, Set<String> subtree, Set<String> visible) {

    /** Sees everything. */
    public static final DataScope ALL = new DataScope(false, Set.of(), Set.of());

    public DataScope {
        subtree = Set.copyOf(subtree);
        visible = Set.copyOf(visible);
    }

    /** True when a person assigned to this department may be seen. */
    public boolean seesPeopleIn(String unitId) {
        return !restricted || subtree.contains(unitId);
    }

    /** True when this department may be shown (listed, searched, opened). */
    public boolean seesUnit(String unitId) {
        return !restricted || visible.contains(unitId);
    }
}
