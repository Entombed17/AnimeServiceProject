package com.kenshin.animetrackerserver.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApiException {

    public UserAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
