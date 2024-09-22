package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FcmMessage {
	@JsonProperty("name")
	private String name;

	@JsonProperty("token")
	private String token;

	@JsonProperty("topic")
	private String topic = "news";

	@JsonProperty("data")
	private Map<String, Object> data = new HashMap<>();

	@JsonProperty("notification")
	private FcmNotification notification;

	@JsonProperty("android")
	private FCMMessageAndroid android = new FCMMessageAndroid();

	@JsonProperty("apns")
	private FCMMessageAPNS apns = new FCMMessageAPNS();

	private FcmMessage(String token) {
		super();
		this.token = token;
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

	public String getToken() {
		return token;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public String getTopic() {
		return topic;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public FCMMessageAndroid getAndroid() {
		return android;
	}

	public void setAndroid(FCMMessageAndroid android) {
		this.android = android;
	}

	public FCMMessageAPNS getApns() {
		return apns;
	}

	public void setApns(FCMMessageAPNS apns) {
		this.apns = apns;
	}
}