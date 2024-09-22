package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FCMMessageAPNSPayloadAPS {
    @JsonProperty("category")
    private String category = null;
    @JsonProperty("badge")
    private String badge = null;
    @JsonProperty("sound")
    private String sound = null;
    @JsonProperty("mutable-content")
    private String mutableContent = "1";
    @JsonProperty("content-available")
    private String contentAvailable = "1";

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public String getSound() {
        return sound;
    }

    public void setSound(String sound) {
        this.sound = sound;
    }

    public String getMutableContent() {
        return mutableContent;
    }

    public void setMutableContent(String mutableContent) {
        this.mutableContent = mutableContent;
    }

    public String getContentAvailable() {
        return contentAvailable;
    }

    public void setContentAvailable(String contentAvailable) {
        this.contentAvailable = contentAvailable;
    }
}
