package com.gracelogic.platform.oauth.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.util.Map;

@Service("apple")
public class AppleOAuthServiceProviderImpl extends AbstractOauthProvider implements OAuthServiceProvider {
    private static Logger logger = Logger.getLogger(AppleOAuthServiceProviderImpl.class);

    private String ACCESS_TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token";
    private String INFO_ENDPOINT = null;
    private String CLIENT_ID = null;
    private String CLIENT_SECRET = null;

    @Autowired
    private IdObjectService idObjectService;

    private MappingJackson2HttpMessageConverter c = new MappingJackson2HttpMessageConverter();
    private ObjectMapper objectMapper = c.getObjectMapper();

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User processAuthorization(String code, String token, String redirectUri) throws InvalidIdentifierException, InvalidPassphraseException, CustomLocalizedException {
        logger.info("Apple");
        logger.info("code: " + code);
        logger.info("token: " + token);

        throw new InvalidIdentifierException();

//        String sRedirectUri = redirectUri;
//        if (StringUtils.isEmpty(redirectUri)) {
//            sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.APPLE.name());
//
//            try {
//                sRedirectUri = URLEncoder.encode(sRedirectUri, "UTF-8");
//            } catch (Exception ignored) {
//            }
//        }
//
//        Map response = null;
//
//        String idToken = null;
//        response = OAuthUtils.postTextBodyReturnJson(ACCESS_TOKEN_ENDPOINT, String.format("code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code", code, CLIENT_ID, CLIENT_SECRET, sRedirectUri));
//        idToken = response != null && response.get("id_token") != null ? (String) response.get("id_token") : null;
//        logger.info("idToken:" + idToken);
//        if (idToken == null) {
//            return null;
//        }
//
//        String decodedIdToken = decodeJWTBody(idToken);
//        logger.info("decodedIdToken:" + decodedIdToken);
//        try {
//            Map<Object, Object> json = objectMapper.readValue(decodedIdToken, new TypeReference<Map<Object, Object>>() {
//            });
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//            return null;
//        }
//
//        OAuthDTO OAuthDTO = new OAuthDTO();
//        OAuthDTO.setAccessToken(idToken);
//        OAuthDTO.setUserId(response.get("sub") != null ? (String) response.get("sub") : null);
//        logger.info("sub:" + response.get("sub"));
//        OAuthDTO.setEmail(response.get("email") != null ? (String) response.get("email") : null);
//        logger.info("email:" + response.get("email"));
//
//        return processAuthorization(DataConstants.OAuthProviders.APPLE.getValue(), OAuthDTO);
    }

    @Override
    public String buildAuthRedirect() {
        String sRedirectUri = buildRedirectUri(null);

        return String.format("https://appleid.apple.com/auth/authorize?response_type=code&scope=email%%20profile&client_id=%s&redirect_uri=%s", CLIENT_ID, sRedirectUri);
    }

    @Override
    public String buildRedirectUri(String additionalParameters) {
        String sRedirectUri = getRedirectUrl(DataConstants.OAuthProviders.APPLE.name());
        if (!StringUtils.isEmpty(additionalParameters)) {
            sRedirectUri = sRedirectUri + additionalParameters;
        }
        try {
            sRedirectUri = URLEncoder.encode(sRedirectUri, "UTF-8");
        } catch (Exception ignored) {
        }

        return sRedirectUri;
    }

    @PostConstruct
    public void init() {
        AuthProvider authProvider = idObjectService.getObjectById(AuthProvider.class, DataConstants.OAuthProviders.APPLE.getValue());
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

    private static String decodeJWTBody(String jwtToken) {
        String[] split_string = jwtToken.split("\\.");
        String base64EncodedHeader = split_string[0];
        String base64EncodedBody = split_string[1];
        String base64EncodedSignature = split_string[2];

        Base64 base64Url = new Base64(true);
        String header = new String(base64Url.decode(base64EncodedHeader));

        String body = new String(base64Url.decode(base64EncodedBody));
        return body;
    }
}
