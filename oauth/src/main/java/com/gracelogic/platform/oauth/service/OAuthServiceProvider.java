package com.gracelogic.platform.oauth.service;

import com.gracelogic.platform.user.model.User;

public interface OAuthServiceProvider {
    User processAuthorization(String code, String token, String redirectUri);

    String buildAuthRedirect();

    String buildRedirectUri(String additionalParameters);
}
