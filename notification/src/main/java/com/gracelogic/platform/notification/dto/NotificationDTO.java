package com.gracelogic.platform.notification.dto;

import com.gracelogic.platform.db.dto.IdObjectDTO;
import com.gracelogic.platform.notification.model.Notification;
import com.gracelogic.platform.notification.model.NotificationMethod;
import com.gracelogic.platform.notification.model.NotificationState;
import com.gracelogic.platform.notification.service.DataConstants;

import java.util.UUID;

public class NotificationDTO extends IdObjectDTO {

    private UUID notificationStateId;
    private UUID notificationMethodId;

    private String source;
    private String destination;
    private String content;
    private Integer priority;

    public static NotificationDTO prepare(Notification model) {
        NotificationDTO dto = new NotificationDTO();
        IdObjectDTO.prepare(dto, model);

        if (model.getNotificationState() != null) {
            dto.setNotificationStateId(model.getNotificationState().getId());
        }
        if (model.getNotificationMethod() != null) {
            dto.setNotificationMethodId(model.getNotificationMethod().getId());
        }
        dto.setSource(model.getSource());
        dto.setDestination(model.getDestination());
        dto.setContent(model.getContent());
        dto.setPriority(model.getPriority());
        return dto;
    }

    public UUID getNotificationStateId() {
        return notificationStateId;
    }

    public void setNotificationStateId(UUID notificationStateId) {
        this.notificationStateId = notificationStateId;
    }

    public UUID getNotificationMethodId() {
        return notificationMethodId;
    }

    public void setNotificationMethodId(UUID notificationMethodId) {
        this.notificationMethodId = notificationMethodId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public NotificationDTO() {
    }

    public NotificationDTO(String source, String destination, String content, Integer priority, UUID notificationMethodId, UUID notificationStateId) {
        this.notificationStateId = notificationStateId;
        this.notificationMethodId = notificationMethodId;
        this.source = source;
        this.destination = destination;
        this.content = content;
        this.priority = priority;
    }
}
