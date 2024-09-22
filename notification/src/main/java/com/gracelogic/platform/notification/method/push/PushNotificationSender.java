package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

@Service("pushNotificationSender")
public class PushNotificationSender implements NotificationSender {
    @Autowired
    private PropertyService propertyService;

    GoogleCredentials googleCredentials = null;

    private static final String FCM_SERVICE_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

    private static Logger logger = Logger.getLogger(PushNotificationSender.class);

    @Override
    public NotificationSenderResult send(String source, String destination, Content content) {
        try {
            String url = String.format(FCM_SERVICE_URL, getProjectId());
            HttpPost post = new HttpPost(url);
            post.addHeader("Authorization", "Bearer " + getAccessToken());
            ObjectMapper mapper = new ObjectMapper();
            FcmMessage fcmMessage = createFcmMessage(destination, content);
            String json = mapper.writeValueAsString(new FCMRequest(fcmMessage));
            logger.info("FCM url: " + url);
            logger.info("FCM request: " + json);

            StringEntity entity = new StringEntity(json, "UTF-8");
            entity.setContentType("application/json");
            post.setEntity(entity);
            HttpResponse httpResponse = HttpUtils.createTrustAllSecuredHttpClient().execute(post);

            String responseJson = EntityUtils.toString(httpResponse.getEntity());
            logger.info("Response received: " + httpResponse.getStatusLine() + "; content: " + responseJson);

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                return new NotificationSenderResult(false, responseJson);
            }
        } catch (IOException ex) {
            logger.error("Failed to send firebase push", ex);
            return new NotificationSenderResult(false, ex.getMessage());
        }

        return new NotificationSenderResult(true, null);
    }

    private FcmMessage createFcmMessage(String destination, Content content) {
        FcmMessage message = FcmMessage.to(destination);

        if (content.getTitle() != null || content.getBody() != null) {
            FcmNotification fcmNotification = new FcmNotification();
            fcmNotification.setTitle(content.getTitle());
            fcmNotification.setBody(content.getBody());

            if (content.getFields().get("badge") != null) {
                message.getApns().getPayload().getAps().setBadge((String) content.getFields().get("badge"));
            }
            if (content.getFields().get("sound") != null) {
                message.getApns().getPayload().getAps().setSound((String) content.getFields().get("sound"));
                message.getAndroid().getNotification().setSound((String) content.getFields().get("sound"));

            }
            if (content.getFields().get("clickAction") != null) {
                message.getAndroid().getNotification().setClick_action((String) content.getFields().get("category"));
                message.getApns().getPayload().getAps().setCategory((String) content.getFields().get("category"));
            }

            message.setNotification(fcmNotification);
        }

        if (content.getFields() != null) {
            for (String key : content.getFields().keySet()) {
                message.getData().put(key, content.getFields().get(key));
            }
        }

        return message;
    }

    @Override
    public boolean supports(UUID notificationMethodId) {
        return notificationMethodId != null && notificationMethodId.equals(DataConstants.NotificationMethods.PUSH.getValue());
    }

    private String getAccessToken() throws IOException {
        initGoogleCredentials();
        return googleCredentials.getAccessToken().getTokenValue();
    }

    private void initGoogleCredentials() throws IOException {
        if (googleCredentials == null) {
            googleCredentials = GoogleCredentials.fromStream(new FileInputStream(propertyService.getPropertyValue("notification:google_services_file")))
                    .createScoped("https://www.googleapis.com/auth/firebase.messaging");
            googleCredentials.refreshIfExpired();
        }
    }

    private String getProjectId() throws IOException {
        return propertyService.getPropertyValue("notification:google_services_project_id");
    }
}
