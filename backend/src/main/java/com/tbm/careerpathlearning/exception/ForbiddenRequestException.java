package com.tbm.careerpathlearning.exception;

public class ForbiddenRequestException extends RuntimeException {

    private String title;

    public ForbiddenRequestException(String title, String message, Throwable cause) {
        super(message, cause);
        this.title = title;
    }

    public ForbiddenRequestException(String title, String message) {
        super(message);
        this.title = title;
    }

    public ForbiddenRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public ForbiddenRequestException(String message) {
        super(message);
    }

    public ForbiddenRequestException(Throwable cause) {
        super(cause);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
