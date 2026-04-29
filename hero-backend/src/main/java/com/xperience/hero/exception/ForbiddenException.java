package com.xperience.hero.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) { super(message); }
    @Override public HttpStatus status() { return HttpStatus.FORBIDDEN; }
}
