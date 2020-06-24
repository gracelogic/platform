package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class FcmMessage {
	@JsonProperty("to")
	private final String to;

	@JsonProperty("priority")
	private final String priority = "high";

	@JsonProperty("content_available")
	private boolean contentAvailable = true;

	@JsonProperty("mutable_content")
	private boolean mutableContent = true;

	@JsonProperty("data")
	private final Map<String, Object> data = new HashMap<>();

	@JsonProperty("notification")
	private FcmNotification notification;

	@JsonProperty("time_to_live")
	private Long timeToLive = 0L;
	
	private FcmMessage(String to) {
		super();
		this.to = to;
	}

	public static FcmMessage to(String to) {
		return new FcmMessage(to);
	}

	public FcmMessage data(Map<String, ?> data) {
		if (data != null) {
			for (Map.Entry<String, ?> entry : data.entrySet()) {
				data(entry.getKey(), entry.getValue());
			}
		}
		return this;
	}

	public FcmMessage data(String key, Object value) {
		data.put(key, value);
		return this;
	}

	public FcmNotification getNotification() {
		return notification;
	}

	public void setNotification(FcmNotification notification) {
		this.notification = notification;
	}

	public String getTo() {
		return to;
	}

	public String getPriority() {
		return priority;
	}

	public boolean isContentAvailable() {
		return contentAvailable;
	}

	public void setContentAvailable(boolean contentAvailable) {
		this.contentAvailable = contentAvailable;
	}

	public boolean isMutableContent() {
		return mutableContent;
	}

	public void setMutableContent(boolean mutableContent) {
		this.mutableContent = mutableContent;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public Long getTimeToLive() {
		return timeToLive;
	}

	public void setTimeToLive(Long timeToLive) {
		this.timeToLive = timeToLive;
	}


}