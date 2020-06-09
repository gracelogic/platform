package com.gracelogic.platform.oauth.service.impl;

import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.oauth.DataConstants;
import com.gracelogic.platform.oauth.dto.OAuthDTO;
import com.gracelogic.platform.oauth.model.AuthProvider;
import com.gracelogic.platform.oauth.service.AbstractOauthProvider;
import com.gracelogic.platform.oauth.service.OAuthServiceProvider;
import com.gracelogic.platform.oauth.service.OAuthUtils;
import com.gracelogic.platform.user.exception.CustomLocalizedException;
import com.gracelogic.platform.user.exception.InvalidIdentifierException;
import com.gracelogic.platform.user.exception.InvalidPassphraseException;
import com.gracelogic.platform.user.model.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.util.Map;

@Service("instagram")
public class InstagramOAuthServiceProviderImpl extends AbstractOauthProvider implements OAuthServiceProvider {
    private String ACCESS_TOKEN_ENDPOINT = "https://api.instagram.com/oauth/access_token";
    private String CLIENT_ID = null;
    private String CLIENT_SECRET = null;

    @Autowired
    private IdObjectService idObjectService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User processAuthorization(String code, String accessToken, String redirectUri) throws InvalidIdentifierException, InvalidPassphraseException, CustomLocalizedException {
        String sRedirectUri = redirectUri;
        if (StringUtils.isEmpty(redirectUri)) {
            sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.INSTAGRAM.name());

            try {
                sRedirectUri = URLEncoder.encode(sRedirectUri, "UTF-8");
            }
            catch (Exception ignored) {}
        }

        Map response = OAuthUtils.postTextBodyReturnJson(ACCESS_TOKEN_ENDPOINT, String.format("grant_type=authorization_code&client_id=%s&client_secret=%s&code=%s&redirect_uri=%s", CLIENT_ID, CLIENT_SECRET, code, sRedirectUri));

        if (response == null) {
            return null;
        }

        OAuthDTO OAuthDTO = new OAuthDTO();
        OAuthDTO.setAccessToken(response.get("access_token") != null ? (String) response.get("access_token") : null);

        response = (Map) response.get("user");

        OAuthDTO.setUserId(response.get("id") != null ? (String) response.get("id") : null);
        String fullName = response.get("full_name") != null ? (String) response.get("full_name") : null;
        String name = null;
        String surname = null;
        if (!StringUtils.isEmpty(fullName)) {
            int spacePos = fullName.indexOf(" ");
            if (spacePos != -1) {
                name = fullName.substring(0, spacePos);
                if (spacePos < fullName.length()) {
                    surname = fullName.substring(spacePos + 1);
                }
            }
            else {
                name = fullName;
            }
        }

        OAuthDTO.setFirstName(name);
        OAuthDTO.setLastName(surname);

        return processAuthorization(DataConstants.OAuthProviders.INSTAGRAM.getValue(), OAuthDTO);
    }

    @Override
    public String buildAuthRedirect() {
        String sRedirectUri = buildRedirectUri(null);

        return String.format("https://api.instagram.com/oauth/authorize/?response_type=code&client_id=%s&redirect_uri=%s", CLIENT_ID, sRedirectUri);
    }

    @Override
    public String buildRedirectUri(String additionalParameters) {
        String sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.INSTAGRAM.name());
        if (!StringUtils.isEmpty(additionalParameters)) {
            sRedirectUri = sRedirectUri + additionalParameters;
        }
        try {
            sRedirectUri = URLEncoder.encode(sRedirectUri, "UTF-8");
        }
        catch (Exception ignored) {}

        return sRedirectUri;
    }

    @PostConstruct
    public void init() {
        AuthProvider authProvider = idObjectService.getObjectById(AuthProvider.class, DataConstants.OAuthProviders.INSTAGRAM.getValue());
        if (authProvider != null) {
            if (authProvider.getAccessTokenEndpoint() != null) {
                ACCESS_TOKEN_ENDPOINT = authProvider.getAccessTokenEndpoint();
            }
            CLIENT_ID = authProvider.getClientId();
            CLIENT_SECRET = authProvider.getClientSecret();
        }
    }
}
