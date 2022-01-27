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
import java.util.ArrayList;
import java.util.Map;

@Service("vk")
public class VkOAuthServiceProviderImpl extends AbstractOauthProvider implements OAuthServiceProvider {
    private String ACCESS_TOKEN_ENDPOINT = "https://oauth.vk.com/access_token";
    private String INFO_ENDPOINT = "https://api.vk.com/method/users.get";
    private String CLIENT_ID = null;
    private String CLIENT_SECRET = null;

    @Autowired
    private IdObjectService idObjectService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User processAuthorization(String code, String accessToken, String redirectUri) throws InvalidIdentifierException, InvalidPassphraseException, CustomLocalizedException {
        String sRedirectUri = redirectUri;
        if (StringUtils.isEmpty(redirectUri)) {
            sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.VK.name());

            try {
                sRedirectUri = URLEncoder.encode(sRedirectUri, "UTF-8");
            }
            catch (Exception ignored) {}
        }


        Map response = OAuthUtils.getQueryReturnJson(String.format("%s?client_id=%s&client_secret=%s&code=%s&redirect_uri=%s", ACCESS_TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, code, sRedirectUri));

        if (response == null) {
            return null;
        }

        OAuthDTO OAuthDTO = new OAuthDTO();
        OAuthDTO.setAccessToken(response.get("access_token") != null ? (String) response.get("access_token") : null);
        OAuthDTO.setUserId(response.get("user_id") != null ? String.valueOf(response.get("user_id")) : null);
        OAuthDTO.setEmail(response.get("email") != null ? (String) response.get("email") : null);

        response = OAuthUtils.getQueryReturnJson(String.format("%s?user_ids=%s&v=5.27&fields=photo_100,city,verified,contacts&access_token=%s", INFO_ENDPOINT, OAuthDTO.getUserId(), OAuthDTO.getAccessToken()));

        response = (Map) ((ArrayList) response.get("response")).iterator().next();

        OAuthDTO.setFirstName(response.get("first_name") != null ? (String) response.get("first_name") : null);
        OAuthDTO.setLastName(response.get("last_name") != null ? (String) response.get("last_name") : null);
        OAuthDTO.setPhone(response.get("mobile_phone") != null ? (String) response.get("mobile_phone") : null);

        return processAuthorization(DataConstants.OAuthProviders.VK.getValue(), OAuthDTO);
    }

    @Override
    public String buildAuthRedirect(String redirectUri) {
        String sRedirectUri = buildRedirectUri(null, redirectUri);

        return String.format("https://oauth.vk.com/authorize?response_type=code&scope=email,phone&display=popup&client_id=%s&redirect_uri=%s", CLIENT_ID, sRedirectUri);
    }

    @Override
    public String buildRedirectUri(String additionalParameters, String redirectUri) {
        String sRedirectUri = redirectUri != null ? redirectUri : getRedirectUrl(DataConstants.OAuthProviders.VK.name());
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
        AuthProvider authProvider = idObjectService.getObjectById(AuthProvider.class, DataConstants.OAuthProviders.VK.getValue());
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
