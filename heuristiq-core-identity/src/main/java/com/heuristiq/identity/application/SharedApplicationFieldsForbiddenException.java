package com.heuristiq.uam.application;

import org.springframework.http.HttpStatus;

import com.heuristiq.uam.common.UamException;

/**
 * An organization administrator tried to change the name or description of an application. Those two fields live on the
 * platform's single row for the application code, which every organization shares, so only a SUPER_ADMIN may change them (403).
 */
public class SharedApplicationFieldsForbiddenException extends UamException {

    public SharedApplicationFieldsForbiddenException() {
        super(ApplicationConstants.SHARED_FIELDS_FORBIDDEN, HttpStatus.FORBIDDEN);
    }
}
