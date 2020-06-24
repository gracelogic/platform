package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.notification.dto.Content;
import com.gracelogic.platform.notification.dto.NotificationSenderResult;
import com.gracelogic.platform.notification.service.HttpUtils;
import com.gracelogic.platform.notification.service.DataConstants;
import com.gracelogic.platform.notification.service.NotificationSender;
import com.gracelogic.platform.property.service.PropertyService;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service("pushNotificationSender")
public class PushNotificationSender implements NotificationSender {
    @Autowired
    private PropertyService propertyService;

    private static final String FCM_SERVICE_URL = "https://fcm.googleapis.com/fcm/send";

    private static Logger logger = Logger.getLogger(PushNotificationSender.class);

    @Override
    public NotificationSenderResult send(String source, String destination, Content content) {
        HttpPost post = new HttpPost(FCM_SERVICE_URL);
        post.addHeader("Authorization", "key=" + propertyService.getPropertyValue("notification:firebase_auth_key"));
        try {
            ObjectMapper mapper = new ObjectMapper();
            FcmMessage fcmMessage = createFcmMessage(destination, content);
            String json = mapper.writeValueAsString(fcmMessage);
            logger.info("FCM request: " + json);

            StringEntity entity = new StringEntity(json, "UTF-8");
            entity.setContentType("application/json");
            post.setEntity(entity);
            HttpResponse httpResponse = HttpUtils.createTrustAllSecuredHttpClient().execute(post);

            String responseJson = EntityUtils.toString(httpResponse.getEntity());
            logger.info("Response received: " + httpResponse.getStatusLine() + "; content: " + responseJson);

            FcmResponse response = mapper.readValue(responseJson, FcmResponse.class);
            String error = response.getResults().iterator().next().getError();
            if (error != null) {
                return new NotificationSenderResult(false, error);
            }
        } catch (IOException ex) {
            return new NotificationSenderResult(false, ex.getMessage());
        }

        return new NotificationSenderResult(true, null);
    }

    private FcmMessage createFcmMessage(String destination, Content content) {
        FcmMessage request = FcmMessage.to(destination);

        if (content.getTitle() != null && content.getBody() != null) {
            FcmNotification fcmNotification = new FcmNotification();
            fcmNotification.setTitle(content.getTitle());
            fcmNotification.setBody(content.getBody());

            if (content.getFields().get("category") != null) {
                fcmNotification.setCategory((String) content.getFields().get("category"));
            }
            if (content.getFields().get("badge") != null) {
                fcmNotification.setBadge((String) content.getFields().get("badge"));
            }
            if (content.getFields().get("sound") != null) {
                fcmNotification.setSound((String) content.getFields().get("sound"));
            }
            if (content.getFields().get("clickAction") != null) {
                fcmNotification.setClickAction((String) content.getFields().get("clickAction"));
            }

            request.setNotification(fcmNotification);
        }

        if (content.getFields() != null) {
            for (String key : content.getFields().keySet()) {
                request.getData().put(key, content.getFields().get(key));
            }
        }

        request.setTimeToLive(0L);
        return request;
    }

    @Override
    public boolean supports(UUID notificationMethodId) {
        return notificationMethodId != null && notificationMethodId.equals(DataConstants.NotificationMethods.PUSH.getValue());
    }

}
