// backend/src/main/java/com/tbm/careerpathlearning/model/Message.java
package com.tbm.careerpathlearning.model;

public class Message {
    private String content;
    private String author;

    // Default constructor for Jackson (JSON serialization/deserialization)
    public Message() {}

    public Message(String content, String author) {
        this.content = content;
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public String toString() {
        return "Message{" +
                "content='" + content + '\'' +
                ", author='" + author + '\'' +
                '}';
    }
}