package com.gracelogic.platform.oauth.service.impl;

import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.oauth.DataConstants;
import com.gracelogic.platform.oauth.dto.OAuthDTO;
import com.gracelogic.platform.oauth.model.AuthProvider;
import com.gracelogic.platform.oauth.service.AbstractOauthProvider;
import com.gracelogic.platform.oauth.service.OAuthServiceProvider;
import com.gracelogic.platform.oauth.service.OAuthUtils;
import com.gracelogic.platform.user.model.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Map;

@Service("google")
public class GoogleOAuthServiceProviderImpl extends AbstractOauthProvider implements OAuthServiceProvider {
    private static Logger logger = Logger.getLogger(GoogleOAuthServiceProviderImpl.class);

    private String ACCESS_TOKEN_ENDPOINT = "https://accounts.google.com/o/oauth2/token";
    private String INFO_ENDPOINT = "https://www.googleapis.com/plus/v1/people/me?access_token=%s&fields=id,name,emails";
    private String CLIENT_ID = null;
    private String CLIENT_SECRET = null;

    @Autowired
    private IdObjectService idObjectService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User processAuthorization(String code, String token, String redirectUri) {
        String sRedirectUri = redirectUri;
        if (StringUtils.isEmpty(redirectUri)) {
            sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.GOOGLE.name());

            try {
                sRedirectUri = URLEncoder.encode(sRedirectUri, "UTF-8");
            }
            catch (Exception ignored) {}
        }

        Map response = null;

        String accessToken = token;
        if (accessToken == null) {
            response = OAuthUtils.postTextBodyReturnJson(ACCESS_TOKEN_ENDPOINT, String.format("code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code", code, CLIENT_ID, CLIENT_SECRET, sRedirectUri));
            accessToken = response != null && response.get("access_token") != null ? (String) response.get("access_token") : null;
            if (accessToken == null) {
                return null;
            }
        }

        OAuthDTO OAuthDTO = new OAuthDTO();
        OAuthDTO.setAccessToken(accessToken);

        response = OAuthUtils.getQueryReturnJson(String.format(INFO_ENDPOINT, OAuthDTO.getAccessToken()));
        OAuthDTO.setUserId(response.get("id") != null ? (String) response.get("id") : null);

        Map name = (Map) response.get("name");
        if (name != null) {
            OAuthDTO.setFirstName(name.get("givenName") != null ? (String) name.get("givenName") : null);
            OAuthDTO.setLastName(name.get("familyName") != null ? (String) name.get("familyName") : null);
        }

        Collection<Map> emails = (Collection<Map>) response.get("emails");
        if (emails != null && !emails.isEmpty()) {
            Map email = emails.iterator().next();
            OAuthDTO.setEmail(email.get("value") != null ? (String) email.get("value") : null);
        }

        return processAuthorization(DataConstants.OAuthProviders.GOOGLE.getValue(), code, OAuthDTO);
    }

    @Override
    public String buildAuthRedirect() {
        String sRedirectUri = buildRedirectUri(null);

        return String.format("https://accounts.google.com/o/oauth2/auth?response_type=code&scope=email&client_id=%s&redirect_uri=%s", CLIENT_ID, sRedirectUri);
    }

    @Override
    public String buildRedirectUri(String additionalParameters) {
        String sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.GOOGLE.name());
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
        AuthProvider authProvider = idObjectService.getObjectById(AuthProvider.class, DataConstants.OAuthProviders.GOOGLE.getValue());
        if (authProvider != null) {
            if (authProvider.getAccessTokenEndpoint() != null) {
                ACCESS_TOKEN_ENDPOINT = authProvider.getAccessTokenEndpoint();
            }
            if (authProvider.getInfoEndpoint() != null) {
                INFO_ENDPOINT = authProvider.getInfoEndpoint();
            }
            CLIENT_ID = authProvider.getClientId();
            CLIENT_SECRET = authProvider.getClientSecret();
        }
    }
}
