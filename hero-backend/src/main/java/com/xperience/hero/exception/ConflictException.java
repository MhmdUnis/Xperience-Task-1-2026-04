package com.xperience.hero.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String message) { super(message); }
    @Override public HttpStatus status() { return HttpStatus.CONFLICT; }
}
