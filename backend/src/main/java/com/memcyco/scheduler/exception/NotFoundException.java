package com.memcyco.scheduler.exception;

import java.util.UUID;

public class NotFoundException extends ApiException {

    public NotFoundException(String resource, UUID id) {
        super(ErrorCode.NOT_FOUND, resource + " " + id + " was not found");
    }
}
