package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FCMRequest {
    @JsonProperty("message")
    private FcmMessage message;

    public FcmMessage getMessage() {
        return message;
    }

    public void setMessage(FcmMessage message) {
        this.message = message;
    }

    public FCMRequest(FcmMessage message) {
        this.message = message;
    }
}
