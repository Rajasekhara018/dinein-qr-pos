package com.heuristiq.identity.dto;

import java.util.List;

/**
 * What the signed-in caller may do, for clients that show or hide screens by privilege: the caller's
 * roles, the permission codes those roles grant (for example {@code TRAIN_ADMIN_VIKALP_READ}) and the
 * numeric privilege ids the gateway enforces. This is a UI hint only: the gateway still checks every request.
 */
public record MyPrivilegesResponse(List<String> roles, List<String> permissions, List<Integer> privilegeIds) {
}
