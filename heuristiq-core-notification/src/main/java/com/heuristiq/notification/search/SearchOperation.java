package com.heuristiq.notification.search;

/** Operation of a record on the search index stream; its {@code name()} is the wire value. */
enum SearchOperation {
    INDEX,
    DELETE
}
