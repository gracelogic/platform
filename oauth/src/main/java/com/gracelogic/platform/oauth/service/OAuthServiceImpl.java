package com.gracelogic.platform.oauth.service;

import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.oauth.DataConstants;
import com.gracelogic.platform.oauth.dto.AuthProviderDTO;
import com.gracelogic.platform.oauth.model.AuthProvider;
import com.gracelogic.platform.user.dto.AuthRequestDTO;
import com.gracelogic.platform.user.dto.IdentifierDTO;
import com.gracelogic.platform.user.dto.UserDTO;
import com.gracelogic.platform.user.exception.*;
import com.gracelogic.platform.user.model.Token;
import com.gracelogic.platform.user.model.User;
import com.gracelogic.platform.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OAuthServiceImpl implements OAuthService {
    @Autowired
    private IdObjectService idObjectService;

    @Qualifier("vk")
    @Autowired
    private OAuthServiceProvider vk;

    @Qualifier("ok")
    @Autowired
    private OAuthServiceProvider ok;

    @Qualifier("instagram")
    @Autowired
    private OAuthServiceProvider instagram;

    @Qualifier("facebook")
    @Autowired
    private OAuthServiceProvider facebook;

    @Qualifier("google")
    @Autowired
    private OAuthServiceProvider google;

    @Qualifier("linkedin")
    @Autowired
    private OAuthServiceProvider linkedin;

    @Qualifier("esia")
    @Autowired
    private OAuthServiceProvider esia;

    @Autowired
    private UserService userService;

    @Override
    public List<AuthProviderDTO> getAuthProviders() {
        List<AuthProvider> providers = idObjectService.getList(AuthProvider.class);
        List<AuthProviderDTO> dtos = new LinkedList<>();
        for (AuthProvider provider : providers) {
            AuthProviderDTO dto = AuthProviderDTO.prepare(provider);
            dto.setClientId(provider.getClientId());
            if (dto.getId().equals(DataConstants.OAuthProviders.VK.getValue())) {
                dto.setUrl(vk.buildAuthRedirect());
            } else if (dto.getId().equals(DataConstants.OAuthProviders.OK.getValue())) {
                dto.setUrl(ok.buildAuthRedirect());
            } else if (dto.getId().equals(DataConstants.OAuthProviders.INSTAGRAM.getValue())) {
                dto.setUrl(instagram.buildAuthRedirect());
            } else if (dto.getId().equals(DataConstants.OAuthProviders.FACEBOOK.getValue())) {
                dto.setUrl(facebook.buildAuthRedirect());
            } else if (dto.getId().equals(DataConstants.OAuthProviders.GOOGLE.getValue())) {
                dto.setUrl(google.buildAuthRedirect());
            } else if (dto.getId().equals(DataConstants.OAuthProviders.LINKEDIN.getValue())) {
                dto.setUrl(linkedin.buildAuthRedirect());
            } else if (dto.getId().equals(DataConstants.OAuthProviders.ESIA.getValue())) {
                dto.setUrl(esia.buildAuthRedirect());
            }

            dtos.add(dto);
        }

        return dtos;
    }

    @Override
    public UUID getIdentifierTypeForAuthProvider(UUID authProviderId) {
        if (authProviderId == null) {
            return null;
        }

        if (authProviderId.equals(DataConstants.OAuthProviders.VK.getValue())) {
            return DataConstants.OAuthIdentifierTypes.VK.getValue();
        } else if (authProviderId.equals(DataConstants.OAuthProviders.OK.getValue())) {
            return DataConstants.OAuthIdentifierTypes.OK.getValue();
        } else if (authProviderId.equals(DataConstants.OAuthProviders.FACEBOOK.getValue())) {
            return DataConstants.OAuthIdentifierTypes.FACEBOOK.getValue();
        } else if (authProviderId.equals(DataConstants.OAuthProviders.INSTAGRAM.getValue())) {
            return DataConstants.OAuthIdentifierTypes.INSTAGRAM.getValue();
        } else if (authProviderId.equals(DataConstants.OAuthProviders.LINKEDIN.getValue())) {
            return DataConstants.OAuthIdentifierTypes.LINKEDIN.getValue();
        } else if (authProviderId.equals(DataConstants.OAuthProviders.GOOGLE.getValue())) {
            return DataConstants.OAuthIdentifierTypes.GOOGLE.getValue();
        } else if (authProviderId.equals(DataConstants.OAuthProviders.ESIA.getValue())) {
            return DataConstants.OAuthIdentifierTypes.ESIA.getValue();
        } else {
            return null;
        }
    }

    @Override
    public Token tokenByCode(UUID authProviderId, String code, String token, String remoteAddress) throws ObjectNotFoundException, UserBlockedException, TooManyAttemptsException, NotAllowedIPException, UserNotApprovedException, InvalidIdentifierException {
        User user = null;
        
        if (authProviderId.equals(DataConstants.OAuthProviders.VK.getValue())) {
            user = vk.processAuthorization(code, token, null);
        } else if (authProviderId.equals(DataConstants.OAuthProviders.OK.getValue())) {
            user = ok.processAuthorization(code, token, null);
        } else if (authProviderId.equals(DataConstants.OAuthProviders.FACEBOOK.getValue())) {
            user = facebook.processAuthorization(code, token, null);
        } else if (authProviderId.equals(DataConstants.OAuthProviders.LINKEDIN.getValue())) {
            user = linkedin.processAuthorization(code, token, null);
        } else if (authProviderId.equals(DataConstants.OAuthProviders.INSTAGRAM.getValue())) {
            user = instagram.processAuthorization(code, token, null);
        } else if (authProviderId.equals(DataConstants.OAuthProviders.GOOGLE.getValue())) {
            user = google.processAuthorization(code, token, null);
        } else if (authProviderId.equals(DataConstants.OAuthProviders.ESIA.getValue())) {
            user = esia.processAuthorization(code, token, null);
        }

        if (user == null) {
            throw new ObjectNotFoundException();
        }

        UserDTO userDTO = userService.getUser(user.getId(), false);
        UUID oauthIdentifierTypeId = getIdentifierTypeForAuthProvider(authProviderId);
        AuthRequestDTO authRequestDTO = null;

        for (IdentifierDTO identifierDTO : userDTO.getIdentifiers()) {
            if (identifierDTO.getIdentifierTypeId().equals(oauthIdentifierTypeId)) {
                authRequestDTO = new AuthRequestDTO();
                authRequestDTO.setIdentifierTypeId(identifierDTO.getIdentifierTypeId());
                authRequestDTO.setIdentifierValue(identifierDTO.getValue());
                break;
            }
        }

        if (authRequestDTO == null) {
            throw new ObjectNotFoundException();
        }
        return userService.establishToken(authRequestDTO, remoteAddress, true);
    }
}
