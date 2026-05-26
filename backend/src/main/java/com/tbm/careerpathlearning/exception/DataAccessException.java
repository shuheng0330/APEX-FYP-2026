package com.tbm.careerpathlearning.exception;

public class DataAccessException extends RuntimeException {

    private String title;

    public DataAccessException(String title, String message, Throwable cause) {
        super(message, cause);
        this.title = title;
    }

    public DataAccessException(String title, String message) {
        super(message);
        this.title = title;
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(Throwable cause) {
        super(cause);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
