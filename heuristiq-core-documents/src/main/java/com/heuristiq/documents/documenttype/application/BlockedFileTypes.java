package com.railcab.dms.documenttype.application;

import java.util.Collection;

/** Port to the platform's blocklist of executable file types, which no document type may re-allow. */
public interface BlockedFileTypes {

    /** Blocked extensions with dot, any case. */
    Collection<String> extensions();

    /** Blocked MIME types, any case. */
    Collection<String> contentTypes();
}
