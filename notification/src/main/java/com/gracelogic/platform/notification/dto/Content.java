package com.gracelogic.platform.notification.dto;

import java.util.HashMap;
import java.util.Map;

public class Content {
    private String title;
    private String body;
    private Map<String, Object> fields = new HashMap<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
