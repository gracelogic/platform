package com.gracelogic.platform.oauth.service;

import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.dictionary.service.DictionaryService;
import com.gracelogic.platform.oauth.Path;
import com.gracelogic.platform.oauth.dto.OAuthDTO;
import com.gracelogic.platform.oauth.model.AuthProvider;
import com.gracelogic.platform.oauth.model.AuthProviderLinkage;
import com.gracelogic.platform.property.service.PropertyService;
import com.gracelogic.platform.user.dto.IdentifierDTO;
import com.gracelogic.platform.user.dto.SignUpDTO;
import com.gracelogic.platform.user.dto.UserDTO;
import com.gracelogic.platform.user.model.User;
import com.gracelogic.platform.user.service.DataConstants;
import com.gracelogic.platform.user.service.UserLifecycleService;
import com.gracelogic.platform.user.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractOauthProvider implements OAuthServiceProvider {
    private static Logger logger = Logger.getLogger(AbstractOauthProvider.class);

    @Autowired
    private IdObjectService idObjectService;

    @Autowired
    private UserLifecycleService registrationService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private DictionaryService ds;

    @Autowired
    private UserService userService;


    protected User processAuthorization(UUID authProviderId, String code, OAuthDTO OAuthDTO) {
        Map<String, Object> params = new HashMap<>();
        params.put("externalUserId", OAuthDTO.getUserId());
        params.put("authProviderId", authProviderId);
        List<AuthProviderLinkage> linkages = idObjectService.getList(AuthProviderLinkage.class, "left join fetch el.user", "el.externalUserId=:externalUserId and el.authProvider.id=:authProviderId", params, null, null, null, 1);
        if (!linkages.isEmpty() && linkages.size() == 1) {
            AuthProviderLinkage authProviderLinkage = linkages.iterator().next();
            //Existing user
            return authProviderLinkage.getUser();
        }
        else {
            User user = null;

            //Register new user
            SignUpDTO signUpDTO = new SignUpDTO();
            //UserRegistrationDTO userRegistrationDTO = new UserRegistrationDTO();

            if (!StringUtils.isEmpty(OAuthDTO.getEmail())) {
                IdentifierDTO identifierDTO = new IdentifierDTO();
                identifierDTO.setValue(OAuthDTO.getEmail());
                identifierDTO.setIdentifierTypeId(DataConstants.IdentifierTypes.EMAIL.getValue());
                identifierDTO.setVerified(false);
                identifierDTO.setPrimary(true);
                signUpDTO.getIdentifiers().add(identifierDTO);
            }

            if (!StringUtils.isEmpty(OAuthDTO.getPhone())) {
                IdentifierDTO identifierDTO = new IdentifierDTO();
                identifierDTO.setValue(OAuthDTO.getPhone());
                identifierDTO.setIdentifierTypeId(DataConstants.IdentifierTypes.PHONE.getValue());
                identifierDTO.setVerified(false);
                identifierDTO.setPrimary(true);
                signUpDTO.getIdentifiers().add(identifierDTO);
            }

            signUpDTO.getFields().put(UserDTO.FIELD_NAME, !StringUtils.isEmpty(OAuthDTO.getFirstName()) ? OAuthDTO.getFirstName() : null);
            signUpDTO.getFields().put(UserDTO.FIELD_SURNAME, !StringUtils.isEmpty(OAuthDTO.getLastName()) ? OAuthDTO.getLastName() : null);
            signUpDTO.getFields().put(UserDTO.FIELD_ORG, !StringUtils.isEmpty(OAuthDTO.getOrg()) ? OAuthDTO.getOrg() : null);


            logger.info("Oauth registration: " + signUpDTO.toString());

            try {
                user = registrationService.signUp(signUpDTO);
            }
            catch (Exception e) {
                logger.error("Failed to register user via oauth", e);
            }

            if (user != null) {
                AuthProviderLinkage authProviderLinkage = new AuthProviderLinkage();
                authProviderLinkage.setAuthProvider(ds.get(AuthProvider.class, authProviderId));
                authProviderLinkage.setUser(user);
                authProviderLinkage.setExternalUserId(OAuthDTO.getUserId());
                authProviderLinkage.setCode(code);
                idObjectService.save(authProviderLinkage);
            }

            return user;
        }
    }

    public String getRedirectUrl(String providerName) {
        return String.format("%s%s/%s", propertyService.getPropertyValue("web:api_url"), Path.OAUTH, providerName);
    }
}
