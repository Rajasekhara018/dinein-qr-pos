package com.railcab.dms.document.persistence;

import java.time.Instant;

/**
 * Filters of the lifecycle document search. Every field is optional (null = no filter); the organization is passed
 * separately and is always applied.
 *
 * @param query        id (exact), original file name (contains, case-insensitive) or checksum (prefix); matched literally
 * @param ownerId      exact owner
 * @param category     exact category
 * @param documentType exact document type code
 * @param status       exact status
 * @param scanStatus   exact scan status
 * @param deleted      true = only deleted, false = only active, null = both
 * @param from         createdAt at or after
 * @param to           createdAt at or before
 */
public record DocumentSearchCriteria(String query, String ownerId, String category, String documentType, String status,
                                     String scanStatus, Boolean deleted, Instant from, Instant to) {
}
