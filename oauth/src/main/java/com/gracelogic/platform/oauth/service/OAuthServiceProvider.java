package com.gracelogic.platform.oauth.service;

import com.gracelogic.platform.user.exception.CustomLocalizedException;
import com.gracelogic.platform.user.exception.InvalidIdentifierException;
import com.gracelogic.platform.user.exception.InvalidPassphraseException;
import com.gracelogic.platform.user.model.User;

public interface OAuthServiceProvider {
    User processAuthorization(String code, String accessToken, String redirectUri) throws InvalidIdentifierException, InvalidPassphraseException, CustomLocalizedException;

    String buildAuthRedirect();

    String buildRedirectUri(String additionalParameters);
}
