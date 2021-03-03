package com.gracelogic.platform.notification.method.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class FcmResponse {
	@JsonProperty("multicast_id")
	private long multicastId;

	@JsonProperty("success")
	private int success;

	@JsonProperty("failure")
	private int failure;

	@JsonProperty("canonical_ids")
	private int canonicalIds;

	@JsonProperty("results")
	private List<FcmResult> results ;

	public List<FcmResult> getResults() {
		return results == null ? Collections.<FcmResult>emptyList() : results;
	}

	public long getMulticastId() {
		return multicastId;
	}

	public void setMulticastId(long multicastId) {
		this.multicastId = multicastId;
	}

	public int getSuccess() {
		return success;
	}

	public void setSuccess(int success) {
		this.success = success;
	}

	public int getFailure() {
		return failure;
	}

	public void setFailure(int failure) {
		this.failure = failure;
	}

	public void setResults(List<FcmResult> results) {
		this.results = results;
	}
}