package com.gracelogic.platform.user.service;

import com.gracelogic.platform.db.dto.EntityListResponse;
import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.user.dto.*;
import com.gracelogic.platform.user.exception.*;
import com.gracelogic.platform.user.model.*;
import com.gracelogic.platform.user.security.SessionBasedAuthentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

public interface UserService {
    void changeUserPassword(UUID userId, String newPassword);

    UserSession updateSessionInfo(HttpSession session, SessionBasedAuthentication sessionBasedAuthentication, String userAgent, boolean isDestroying);

    void changePasswordViaVerificationCode(UUID identifierTypeId, String identifierValue, String verificationCode, String newPassword) throws ObjectNotFoundException, InvalidPassphraseException;

    UserSetting getUserSetting(UUID userId, String key);

    void updateUserSetting(UUID userId, String key, String value);

    void deleteUser(User user);

    void addRoleToUser(User user, Collection<UUID> roleIds);

    User saveUser(UserDTO user, boolean mergeRoles, boolean mergeIdentifiers, AuthorizedUser executor) throws ObjectNotFoundException, InvalidIdentifierException, InvalidPassphraseException;

    List<UserRole> getUserRoles(UUID userId);

    void mergeUserRoles(UUID userId, Collection<UUID> activeRoles);

    void mergeUserIdentifiers(User user, boolean isNewUser, Collection<IdentifierDTO> identifierDTOList, boolean throwExceptionIfAlreadyAttached);

    EntityListResponse<UserDTO> getUsersPaged(String identifierValue, Boolean approved, Boolean blocked, Map<String, String> fields, boolean fetchRoles, Integer count, Integer page, Integer start, String sortField, String sortDir);

    UserDTO getUser(UUID userId, boolean fetchRoles) throws ObjectNotFoundException;

    EntityListResponse<RoleDTO> getRolesPaged(String code, String name, boolean fetchGrants, Integer count, Integer page, Integer start, String sortField, String sortDir);

    Role saveRole(RoleDTO dto) throws ObjectNotFoundException;

    RoleDTO getRole(UUID roleId, boolean fetchGrants) throws ObjectNotFoundException;

    void deleteRole(UUID roleId);

    EntityListResponse<UserSessionDTO> getSessionsPaged(UUID userId, String authIp, Date startDate, Date endDate, boolean enrich, Integer count, Integer page, Integer start, String sortField, String sortDir);

    String translateUserSortFieldToNative(String sortFieldInJPAFormat);

    void changeLocale(HttpServletRequest request, AuthorizedUser authorizedUser, String locale) throws IllegalArgumentException;

    boolean isIdentifierValid(UUID identifierTypeId, String identifierValue, boolean checkAvailable);

    Date getUserPasswordExpirationDate(UUID userId) throws ObjectNotFoundException;

    UUID resolveIdentifierTypeId(String identifierValue) throws InvalidIdentifierException;

    boolean isPassphraseValueValid(Passphrase passphrase, String value);

    Passphrase getActualPassphrase(UUID userId, UUID passphraseTypeId, UUID referenceObjectId, boolean archiveExpiredPassphrase);

    Identifier findIdentifier(UUID identifierTypeId, String value, boolean enrich);

    void archiveActualPassphrases(UUID userId, UUID passphraseTypeId, UUID referenceObjectId);

    Passphrase updatePassphrase(User user, String value, UUID passphraseTypeId, UUID referenceObjectId, boolean archiveOtherPassphrases) throws InvalidPassphraseException;

    Identifier processSignIn(UUID identifierTypeId, String identifierValue, String password, String remoteAddress, boolean trust) throws UserBlockedException, TooManyAttemptsException, NotAllowedIPException, UserNotApprovedException, InvalidIdentifierException;

    void sendIdentifierVerificationCode(UUID identifierId, Map<String, String> templateParams);

    Passphrase getActualVerificationCode(User user, UUID referenceObjectId, UUID passphraseTypeId, boolean createNewIfNotExist);

    User processSignUp(SignUpDTO signUpDTO) throws InvalidIdentifierException, InvalidPassphraseException;

    void sendVerificationCodeForPasswordChanging(UUID identifierTypeId, String identifierValue, Map<String, String> templateParams) throws ObjectNotFoundException, TooFastOperationException;

    boolean processIdentifierVerificationViaVerificationCode(UUID identifierTypeId, String identifierValue, String verificationCode);

    void verifyIdentifierViaVerificationCode(UUID identifierTypeId, String identifierValue, String verificationCode) throws ObjectNotFoundException, InvalidPassphraseException;

    void updateTokenLastRequestDate(Token newToken);

    Token establishToken(AuthRequestDTO authRequestDTO, String remoteAddress, boolean trust) throws UserBlockedException, TooManyAttemptsException, NotAllowedIPException, UserNotApprovedException, InvalidIdentifierException;

    void deactivateToken(TokenDTO tokenDTO);

    void blockExpiredUsers();

    void archivePassphrase(Passphrase passphrase);

    EntityListResponse<PassphraseTypeDTO> getPassphraseTypePaged(String name, boolean enrich, Integer count, Integer page, Integer start, String sortField, String sortDir);

    PassphraseTypeDTO getPassphraseType(UUID id) throws ObjectNotFoundException;

    PassphraseType savePassphraseType(PassphraseTypeDTO passphraseTypeDTO) throws ObjectNotFoundException;

    void deletePassphraseType(UUID id);
}
