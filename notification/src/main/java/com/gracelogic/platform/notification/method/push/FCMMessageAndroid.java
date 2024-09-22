package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FCMMessageAndroid {
    @JsonProperty("notification")
    private FCMMessageAndroidNotification notification = new FCMMessageAndroidNotification();

    public FCMMessageAndroidNotification getNotification() {
        return notification;
    }

    public void setNotification(FCMMessageAndroidNotification notification) {
        this.notification = notification;
    }
}
