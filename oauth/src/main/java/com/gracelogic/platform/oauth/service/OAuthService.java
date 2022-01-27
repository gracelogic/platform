package com.gracelogic.platform.oauth.service;

import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.oauth.dto.AuthProviderDTO;
import com.gracelogic.platform.user.dto.TokenDTO;
import com.gracelogic.platform.user.exception.*;
import com.gracelogic.platform.user.model.Token;
import com.gracelogic.platform.user.model.User;

import java.util.List;
import java.util.UUID;

public interface OAuthService {
    List<AuthProviderDTO> getAuthProviders();

    UUID getIdentifierTypeForAuthProvider(UUID authProviderId);

    Token tokenByCode(UUID authProviderId, String code, String accessToken, String redirectUri, String remoteAddress) throws ObjectNotFoundException, UserBlockedException, TooManyAttemptsException, NotAllowedIPException, UserNotApprovedException, InvalidIdentifierException, CustomLocalizedException;
}