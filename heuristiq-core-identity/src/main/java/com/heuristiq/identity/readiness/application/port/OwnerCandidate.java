package com.heuristiq.uam.readiness.application.port;

/**
 * A department head who may be chosen as an owner.
 *
 * @param userId         the head's account id (what the owner team stores)
 * @param name           the head's name
 * @param departmentCode code of the department they head
 * @param departmentName name of the department
 */
public record OwnerCandidate(String userId, String name, String departmentCode, String departmentName) {
}
