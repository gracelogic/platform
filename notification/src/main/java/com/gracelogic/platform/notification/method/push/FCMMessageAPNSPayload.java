package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FCMMessageAPNSPayload {
    @JsonProperty("aps")
    private FCMMessageAPNSPayloadAPS aps = new FCMMessageAPNSPayloadAPS();

    public FCMMessageAPNSPayloadAPS getAps() {
        return aps;
    }

    public void setAps(FCMMessageAPNSPayloadAPS aps) {
        this.aps = aps;
    }
}
