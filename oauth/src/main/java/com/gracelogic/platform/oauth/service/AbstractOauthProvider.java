package com.gracelogic.platform.oauth.service;

import com.gracelogic.platform.oauth.Path;
import com.gracelogic.platform.oauth.dto.OAuthDTO;
import com.gracelogic.platform.property.service.PropertyService;
import com.gracelogic.platform.user.dto.IdentifierDTO;
import com.gracelogic.platform.user.dto.SignUpDTO;
import com.gracelogic.platform.user.dto.UserDTO;
import com.gracelogic.platform.user.model.Identifier;
import com.gracelogic.platform.user.model.User;
import com.gracelogic.platform.user.service.DataConstants;
import com.gracelogic.platform.user.service.UserLifecycleService;
import com.gracelogic.platform.user.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

public abstract class AbstractOauthProvider implements OAuthServiceProvider {
    private static Logger logger = Logger.getLogger(AbstractOauthProvider.class);

    @Autowired
    private UserLifecycleService registrationService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserService userService;

    @Autowired
    private OAuthService oAuthService;

    private String generateRandomPassword() {
        return UUID.randomUUID().toString();
    }

    protected User processAuthorization(UUID authProviderId, String code, OAuthDTO OAuthDTO) {
        UUID oauthIdentifierTypeId = oAuthService.getIdentifierTypeForAuthProvider(authProviderId);
        Identifier identifier = userService.findIdentifier(oauthIdentifierTypeId, OAuthDTO.getUserId(), true);
        if (identifier != null) {
            return identifier.getUser();
        }
        else {
            User user = null;

            //Register new user
            SignUpDTO signUpDTO = new SignUpDTO();

            IdentifierDTO oauthIdentifierDTO = new IdentifierDTO();
            oauthIdentifierDTO.setValue(OAuthDTO.getUserId());
            oauthIdentifierDTO.setIdentifierTypeId(oauthIdentifierTypeId);
            oauthIdentifierDTO.setVerified(true);
            oauthIdentifierDTO.setPrimary(true);
            signUpDTO.getIdentifiers().add(oauthIdentifierDTO);

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

            signUpDTO.setPassword(generateRandomPassword());

            logger.info("Oauth registration: " + signUpDTO.toString());

            try {
                user = registrationService.signUp(signUpDTO);
            }
            catch (Exception e) {
                logger.error("Failed to register user via oauth", e);
            }

            return user;
        }
    }

    public String getRedirectUrl(String providerName) {
        return String.format("%s%s/%s", propertyService.getPropertyValue("web:api_url"), Path.OAUTH, providerName);
    }
}
