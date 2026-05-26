package com.tbm.careerpathlearning.exception;

public class BadRequestException extends RuntimeException {

    private String title;

    public BadRequestException(String title, String message, Throwable cause) {
        super(message, cause);
        this.title = title;
    }

    public BadRequestException(String title, String message) {
        super(message);
        this.title = title;
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(Throwable cause) {
        super(cause);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
