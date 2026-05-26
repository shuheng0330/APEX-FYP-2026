package com.tbm.careerpathlearning.exception;

public class InternalServerException extends RuntimeException {

    private String title;

    public InternalServerException(String title, String message, Throwable cause) {
        super(message, cause);
        this.title = title;
    }

    public InternalServerException(String title, String message) {
        super(message);
        this.title = title;
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public InternalServerException(String message) {
        super(message);
    }

    public InternalServerException(Throwable cause) {
        super(cause);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
