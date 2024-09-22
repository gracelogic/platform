package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FCMMessageAPNSPayloadAPS {
    @JsonProperty("category")
    private String category = null;
    @JsonProperty("badge")
    private Integer badge = null;
    @JsonProperty("sound")
    private String sound = null;
    @JsonProperty("mutable-content")
    private Integer mutableContent = 1;
    @JsonProperty("content-available")
    private Integer contentAvailable = 1;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getBadge() {
        return badge;
    }

    public void setBadge(Integer badge) {
        this.badge = badge;
    }

    public String getSound() {
        return sound;
    }

    public void setSound(String sound) {
        this.sound = sound;
    }

    public Integer getMutableContent() {
        return mutableContent;
    }

    public void setMutableContent(Integer mutableContent) {
        this.mutableContent = mutableContent;
    }

    public Integer getContentAvailable() {
        return contentAvailable;
    }

    public void setContentAvailable(Integer contentAvailable) {
        this.contentAvailable = contentAvailable;
    }
}
