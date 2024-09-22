package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FCMMessageAPNS {
    @JsonProperty("payload")
    private FCMMessageAPNSPayload payload = new FCMMessageAPNSPayload();

    public FCMMessageAPNSPayload getPayload() {
        return payload;
    }

    public void setPayload(FCMMessageAPNSPayload payload) {
        this.payload = payload;
    }
}
