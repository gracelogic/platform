package com.gracelogic.platform.oauth.service.impl;

import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.oauth.DataConstants;
import com.gracelogic.platform.oauth.dto.OAuthDTO;
import com.gracelogic.platform.oauth.model.AuthProvider;
import com.gracelogic.platform.oauth.service.AbstractOauthProvider;
import com.gracelogic.platform.oauth.service.OAuthServiceProvider;
import com.gracelogic.platform.oauth.service.OAuthUtils;
import com.gracelogic.platform.user.model.User;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.util.Map;

@Service("ok")
public class OkOAuthServiceProviderImpl extends AbstractOauthProvider implements OAuthServiceProvider {
    private String ACCESS_TOKEN_ENDPOINT = "https://api.odnoklassniki.ru/oauth/token.do";
    private String INFO_ENDPOINT = "http://api.odnoklassniki.ru/fb.do";
    private String CLIENT_ID = null;
    private String CLIENT_SECRET = null;
    private String CLIENT_PUBLIC_KEY = null;

    @Autowired
    private IdObjectService idObjectService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User processAuthorization(String code, String token, String redirectUri) {
        String sRedirectUri = redirectUri;
        if (StringUtils.isEmpty(redirectUri)) {
            sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.OK.name());

            try {
                sRedirectUri = URLEncoder.encode(sRedirectUri, "UTF-8");
            }
            catch (Exception ignored) {}
        }

        Map response = OAuthUtils.postJsonBodyReturnJson(String.format("%s?grant_type=authorization_code&client_id=%s&client_secret=%s&code=%s&redirect_uri=%s", ACCESS_TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, code, sRedirectUri), null);

        if (response == null) {
            return null;
        }

        OAuthDTO OAuthDTO = new OAuthDTO();
        OAuthDTO.setAccessToken(response.get("access_token") != null ? (String) response.get("access_token") : null);

        String s = String.format("application_key=%sformat=jsonmethod=users.getCurrentUser", CLIENT_PUBLIC_KEY);
        String md51 = DigestUtils.md5Hex(String.format("%s%s", OAuthDTO.getAccessToken(), CLIENT_SECRET));
        String sign = DigestUtils.md5Hex(String.format("%s%s", s, md51));

        response = OAuthUtils.postJsonBodyReturnJson(String.format("%s?application_key=%s&method=users.getCurrentUser&format=json&access_token=%s&sig=%s", INFO_ENDPOINT, CLIENT_PUBLIC_KEY, OAuthDTO.getAccessToken(), sign), null);

        OAuthDTO.setUserId(response.get("uid") != null ? (String) response.get("uid") : null);
        OAuthDTO.setFirstName(response.get("first_name") != null ? (String) response.get("first_name") : null);
        OAuthDTO.setLastName(response.get("last_name") != null ? (String) response.get("last_name") : null);

        return processAuthorization(DataConstants.OAuthProviders.OK.getValue(), code, OAuthDTO);
    }

    @Override
    public String buildAuthRedirect() {
        String sRedirectUri = buildRedirectUri(null);

        return String.format("https://connect.ok.ru/oauth/authorize?scope=VALUABLE_ACCESS&response_type=code&layout=w&client_id=%s&redirect_uri=%s", CLIENT_ID, sRedirectUri);
    }

    @Override
    public String buildRedirectUri(String additionalParameters) {
        String sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.OK.name());
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
        AuthProvider authProvider = idObjectService.getObjectById(AuthProvider.class, DataConstants.OAuthProviders.OK.getValue());
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
