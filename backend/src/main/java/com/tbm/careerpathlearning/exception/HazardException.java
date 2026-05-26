package com.tbm.careerpathlearning.exception;

public class HazardException extends RuntimeException {

    private String title;

    public HazardException(String title, String message, Throwable cause) {
        super(message, cause);
        this.title = title;
    }

    public HazardException(String title, String message) {
        super(message);
        this.title = title;
    }

    public HazardException(String message, Throwable cause) {
        super(message, cause);
    }

    public HazardException(String message) {
        super(message);
    }

    public HazardException(Throwable cause) {
        super(cause);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
