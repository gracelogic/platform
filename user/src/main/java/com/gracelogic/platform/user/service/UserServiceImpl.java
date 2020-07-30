package com.gracelogic.platform.user.service;

import com.gracelogic.platform.db.JsonUtils;
import com.gracelogic.platform.db.dto.EntityListResponse;
import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.db.service.IdObjectService;
import com.gracelogic.platform.dictionary.service.DictionaryService;
import com.gracelogic.platform.localization.service.LocaleHolder;
import com.gracelogic.platform.notification.dto.Content;
import com.gracelogic.platform.notification.service.NotificationService;
import com.gracelogic.platform.property.service.PropertyService;
import com.gracelogic.platform.notification.service.TemplateService;
import com.gracelogic.platform.user.dao.UserDao;
import com.gracelogic.platform.user.dto.*;
import com.gracelogic.platform.user.exception.*;
import com.gracelogic.platform.user.filter.LocaleFilter;
import com.gracelogic.platform.user.model.*;
import com.gracelogic.platform.user.security.SessionBasedAuthentication;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.log4j.Logger;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("userService")
public class UserServiceImpl implements UserService {
    private static Logger logger = Logger.getLogger(UserServiceImpl.class);

    @Autowired
    private IdObjectService idObjectService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private DictionaryService ds;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserLifecycleService lifecycleService;

    @Autowired
    private TemplateService templateService;


    @PostConstruct
    private void init() {
        //Load user name format
        String userNameFormat = propertyService.getPropertyValue("user:user_name_format");
        if (!StringUtils.isEmpty(userNameFormat)) {
            UserDTO.setUserNameFormat(userNameFormat);
        }

        Boolean oneSessionPerUser = propertyService.getPropertyValueAsBoolean("user:one_session_per_user");
        if (oneSessionPerUser != null && oneSessionPerUser) {
            List<Object[]> lastActiveUsersSession = userDao.getLastActiveUsersSessions();
            logger.info("Loaded last users sessions: " + lastActiveUsersSession.size());
            for (Object[] obj : lastActiveUsersSession) {
                UUID userId = UUID.fromString((String) obj[0]);
                String sessionId = (String) obj[1];

                LastSessionHolder.updateLastSessionSessionId(userId, sessionId);
            }
        }

        //Load default locale
        String defaultLocale = propertyService.getPropertyValue("user:default_locale");
        if (!StringUtils.isEmpty(defaultLocale)) {
            try {
                LocaleHolder.defaultLocale = LocaleUtils.toLocale(defaultLocale);
                ;
            } catch (Exception e) {
                logger.error("Failed to override default locale", e);
            }
        }
    }

    @Transactional
    @Override
    public void changeUserPassword(UUID userId, String newPassword) throws InvalidPassphraseException{
        User user = idObjectService.getObjectById(User.class, userId);
        updatePassphrase(user, newPassword, DataConstants.PassphraseTypes.USER_PASSWORD.getValue(), userId, true);

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        idObjectService.delete(IncorrectAuthAttempt.class, "el.user.id=:userId", params);
    }

    private static String generatePasswordSalt() {
        Random random = new Random(System.currentTimeMillis());
        return DigestUtils.md5Hex(String.valueOf(random.nextLong()));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserSession updateSessionInfo(HttpSession session, SessionBasedAuthentication sessionBasedAuthentication, String userAgent, boolean isDestroying) {
        if (session != null && !StringUtils.isEmpty(session.getId())) {
            SessionBasedAuthentication authentication = null;
            try {
                authentication = (SessionBasedAuthentication) ((org.springframework.security.core.context.SecurityContextImpl) session.getAttribute("SPRING_SECURITY_CONTEXT")).getAuthentication();
            } catch (Exception ignored) {
            }

            if (authentication == null) {
                authentication = sessionBasedAuthentication;
            }

            if (authentication != null && authentication.getDetails() != null && authentication.getDetails() instanceof AuthorizedUser) {
                AuthorizedUser authorizedUser = (AuthorizedUser) authentication.getDetails();
                UserSession userSession = null;

                Map<String, Object> params = new HashMap<>();
                params.put("sessionId", session.getId());

                List<UserSession> userSessions = idObjectService.getList(UserSession.class, null, "el.sessionId=:sessionId", params, "el.created DESC", null, 1);
                if (userSessions != null && !userSessions.isEmpty()) {
                    userSession = userSessions.iterator().next();
                }

                if (userSession == null) {
                    userSession = new UserSession();
                    userSession.setSessionId(session.getId());
                    userSession.setUser(idObjectService.getObjectById(User.class, authorizedUser.getId()));
                    userSession.setAuthIp(authentication.getRemoteAddress());
                    if (authorizedUser.getSignInIdentifier() != null) {
                        userSession.setIdentifier(idObjectService.getObjectById(Identifier.class, authorizedUser.getSignInIdentifier().getId()));
                    }
                    userSession.setUserAgent(userAgent);
                }
                userSession.setSessionCreatedDt(new Date(session.getCreationTime()));
                userSession.setLastAccessDt(new Date(session.getLastAccessedTime()));
                userSession.setMaxInactiveInterval((long) session.getMaxInactiveInterval());
                userSession.setValid(!isDestroying);

                if (!isDestroying) {
                    LastSessionHolder.updateLastSessionSessionId(authorizedUser.getId(), session.getId());
                }

                return idObjectService.save(userSession);

            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendVerificationCodeForPasswordChanging(UUID identifierTypeId, String identifierValue, Map<String, String> templateParams) throws ObjectNotFoundException, TooFastOperationException {
        if (identifierTypeId == null) {
            identifierTypeId = resolveIdentifierTypeId(identifierValue);
        }

        Identifier identifier = findIdentifier(identifierTypeId, identifierValue, false);
        if (identifier != null && identifier.getVerified() && identifier.getUser() != null && identifier.getUser().getApproved()) {
            if (templateParams == null) {
                templateParams = new HashMap<>();
            }
            templateParams.put("userId", identifier.getUser().getId().toString());
            templateParams.put("identifierTypeId", identifierTypeId.toString());
            templateParams.put("identifier", identifierValue);
            templateParams.put("baseUrl", propertyService.getPropertyValue("web:base_url"));
            Map<String, String> fields = JsonUtils.jsonToMap(identifier.getUser().getFields());
            for (String key : fields.keySet()) {
                templateParams.put(key, fields.get(key));
            }

            long currentTimeMills = System.currentTimeMillis();
            Passphrase passphrase = getActualVerificationCode(identifier.getUser(), identifier.getUser().getId(), DataConstants.PassphraseTypes.CHANGE_PASSWORD_VERIFICATION_CODE.getValue(), true);
            if (passphrase.getCreated().getTime() > currentTimeMills || (currentTimeMills - passphrase.getCreated().getTime() > propertyService.getPropertyValueAsLong("user:action_delay"))) {
                if (identifierTypeId.equals(DataConstants.IdentifierTypes.PHONE.getValue())) {
                    try {
                        templateParams.put("verificationCode", passphrase.getValue());

                        Content content = templateService.buildFromTemplate(DataConstants.TemplateTypes.SMS_REPAIRING.getValue(), LocaleHolder.getLocale(), templateParams);
                        notificationService.send(com.gracelogic.platform.notification.service.DataConstants.NotificationMethods.SMS.getValue(),
                                propertyService.getPropertyValue("notification:sms_from"), identifierValue, content, 0);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                } else if (identifierTypeId.equals(DataConstants.IdentifierTypes.EMAIL.getValue())) {
                    try {
                        templateParams.put("verificationCode", passphrase.getValue());

                        Content content = templateService.buildFromTemplate(DataConstants.TemplateTypes.EMAIL_REPAIRING.getValue(), LocaleHolder.getLocale(), templateParams);
                        notificationService.send(com.gracelogic.platform.notification.service.DataConstants.NotificationMethods.EMAIL.getValue(),
                                propertyService.getPropertyValue("notification:smtp_from"), identifierValue, content, 0);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            } else {
                throw new TooFastOperationException();
            }
        } else {
            throw new ObjectNotFoundException();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePasswordViaVerificationCode(UUID identifierTypeId, String identifierValue, String verificationCode, String newPassword) throws ObjectNotFoundException, InvalidPassphraseException {
        if (identifierTypeId == null) {
            identifierTypeId = resolveIdentifierTypeId(identifierValue);
        }

        Identifier identifier = findIdentifier(identifierTypeId, identifierValue, false);
        if (identifier != null && identifier.getVerified() && identifier.getUser() != null && identifier.getUser().getApproved()) {
            Passphrase passphrase = getActualVerificationCode(identifier.getUser(), identifier.getUser().getId(), DataConstants.PassphraseTypes.CHANGE_PASSWORD_VERIFICATION_CODE.getValue(), false);
            if (isPassphraseValueValid(passphrase, verificationCode)) {
                changeUserPassword(identifier.getUser().getId(), newPassword);
                archivePassphrase(passphrase);
            } else {
                throw new InvalidPassphraseException();
            }
        } else {
            throw new ObjectNotFoundException();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyIdentifierViaVerificationCode(UUID identifierTypeId, String identifierValue, String verificationCode) throws ObjectNotFoundException, InvalidPassphraseException {
        if (identifierTypeId == null) {
            identifierTypeId = resolveIdentifierTypeId(identifierValue);
        }

        Identifier identifier = findIdentifier(identifierTypeId, identifierValue, false);
        if (identifier != null && !identifier.getVerified()) {
            Passphrase passphrase = getActualVerificationCode(identifier.getUser(), identifier.getId(), DataConstants.PassphraseTypes.IDENTIFIER_VERIFICATION_CODE.getValue(), false);
            if (isPassphraseValueValid(passphrase, verificationCode)) {
                identifier.setVerified(true);
                idObjectService.save(identifier);
            } else {
                throw new InvalidPassphraseException();
            }
        } else {
            throw new ObjectNotFoundException();
        }
    }

    @Override
    public UserSetting getUserSetting(UUID userId, String key) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("key_setting", key);

        List<UserSetting> userSettings = idObjectService.getList(UserSetting.class, null, "el.user.id=:userId and el.key=:key_setting", params, null, null, null, 1);
        if (!userSettings.isEmpty()) {
            return userSettings.iterator().next();
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserSetting(UUID userId, String key, String value) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("key_setting", key);

        UserSetting userSetting;
        List<UserSetting> userSettings = idObjectService.getList(UserSetting.class, null, "el.user.id=:userId and el.key=:key_setting", params, null, null, null, 1);
        if (!userSettings.isEmpty()) {
            userSetting = userSettings.iterator().next();
        } else {
            userSetting = new UserSetting();
            userSetting.setKey(key);
            userSetting.setUser(idObjectService.getObjectById(User.class, userId));
        }

        userSetting.setValue(value);
        idObjectService.save(userSetting);
    }


    private static Integer generateCode(int maxValue, int minValue) {
        Random random = new Random();
        return minValue + random.nextInt(maxValue - minValue);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public User processSignUp(SignUpDTO signUpDTO) throws InvalidIdentifierException, InvalidPassphraseException {
        String userApproveMethod = propertyService.getPropertyValue("user:approve_method");
        boolean approved = StringUtils.equalsIgnoreCase(userApproveMethod, DataConstants.UserApproveMethods.AUTO.getValue());

        User user = new User();
        user.setApproved(approved);
        user.setBlocked(false);
        user.setFields(JsonUtils.mapToJson(signUpDTO.getFields()));
        user = idObjectService.save(user);

        //Add identifier for processSignIn by userId
        IdentifierDTO identifierDTO = new IdentifierDTO();
        identifierDTO.setValue(user.getId().toString());
        identifierDTO.setIdentifierTypeId(DataConstants.IdentifierTypes.USER_ID.getValue());
        signUpDTO.getIdentifiers().add(identifierDTO);


        mergeUserIdentifiers(user, true, signUpDTO.getIdentifiers(), true);
        updatePassphrase(user, signUpDTO.getPassword(), DataConstants.PassphraseTypes.USER_PASSWORD.getValue(), user.getId(), false);

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(User user) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("userId", user.getId());
        idObjectService.delete(IncorrectAuthAttempt.class, "el.user.id=:userId", params);
        idObjectService.delete(Passphrase.class, "el.user.id=:userId", params);
        idObjectService.delete(UserSession.class, "el.user.id=:userId", params);
        idObjectService.delete(UserRole.class, "el.user.id=:userId", params);
        idObjectService.delete(UserSetting.class, "el.user.id=:userId", params);
        idObjectService.delete(Token.class, "el.user.id=:userId", params);
        idObjectService.delete(Identifier.class, "el.user.id=:userId", params);
        idObjectService.delete(User.class, user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendIdentifierVerificationCode(UUID identifierId, Map<String, String> templateParams) {
        Identifier identifier = idObjectService.getObjectById(Identifier.class, identifierId);
        if (identifier == null || identifier.getVerified() && identifier.getUser() != null) {
            return;
        }

        Passphrase passphrase = getActualVerificationCode(identifier.getUser(), identifierId, DataConstants.PassphraseTypes.IDENTIFIER_VERIFICATION_CODE.getValue(), true);
        if (templateParams == null) {
            templateParams = new HashMap<>();
        }
        templateParams.put("userId", identifier.getUser().getId().toString());
        templateParams.put("identifierTypeId", identifier.getIdentifierType().getId().toString());
        templateParams.put("identifierId", identifier.getId().toString());
        templateParams.put("baseUrl", propertyService.getPropertyValue("web:base_url"));
        templateParams.put("verificationCode", passphrase.getValue());
        Map<String, String> fields = JsonUtils.jsonToMap(identifier.getUser().getFields());
        for (String key : fields.keySet()) {
            templateParams.put(key, fields.get(key));
        }

        if (identifier.getIdentifierType().getId().equals(DataConstants.IdentifierTypes.EMAIL.getValue())) {
            try {
                Content content = templateService.buildFromTemplate(DataConstants.TemplateTypes.EMAIL_VERIFICATION.getValue(), LocaleHolder.getLocale(), templateParams);
                notificationService.send(com.gracelogic.platform.notification.service.DataConstants.NotificationMethods.EMAIL.getValue(),
                        propertyService.getPropertyValue("notification:smtp_from"), identifier.getValue(), content, 0);
            } catch (Exception e) {
                logger.error(e);
            }

        } else if (identifier.getIdentifierType().getId().equals(DataConstants.IdentifierTypes.PHONE.getValue())) {
            try {
                Content content = templateService.buildFromTemplate(DataConstants.TemplateTypes.SMS_VERIFICATION.getValue(), LocaleHolder.getLocale(), templateParams);
                notificationService.send(com.gracelogic.platform.notification.service.DataConstants.NotificationMethods.SMS.getValue(),
                        propertyService.getPropertyValue("notification:smtp_from"), identifier.getValue(), content, 0);

            } catch (Exception e) {
                logger.error(e);
            }

        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addRoleToUser(User user, Collection<UUID> roleIds) {
        for (UUID roleId : roleIds) {
            Role role = idObjectService.getObjectById(Role.class, roleId);

            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);

            idObjectService.save(userRole);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User saveUser(UserDTO userDTO, boolean mergeRoles, boolean mergeIdentifiers, AuthorizedUser executor) throws ObjectNotFoundException, InvalidIdentifierException, InvalidPassphraseException {
        if (userDTO.getId() == null) {
            throw new ObjectNotFoundException();
        }
        User user = idObjectService.getObjectById(User.class, userDTO.getId());
        if (user == null) {
            throw new ObjectNotFoundException();
        }

        user.setFields(JsonUtils.mapToJson(userDTO.getFields()));
        user.setApproved(userDTO.getApproved());
        user.setLocale(userDTO.getLocale());


        if (!user.getBlocked() && userDTO.getBlocked()) {
            user.setBlockedByUser(idObjectService.getObjectById(User.class, executor.getId()));
            user.setBlockedDt(new Date());
        }

        user.setBlocked(userDTO.getBlocked());
        if (!user.getBlocked()) {
            user.setBlockedDt(null);
            user.setBlockedByUser(null);
        }

        if (StringUtils.isEmpty(userDTO.getAuthScheduleCronExpression()) || CronExpression.isValidExpression(userDTO.getAuthScheduleCronExpression())) {
            user.setAuthScheduleCronExpression(userDTO.getAuthScheduleCronExpression());
        }
        user.setBlockAfterDt(userDTO.getBlockAfterDt());

        user = idObjectService.save(user);

        if (mergeRoles) {
            mergeUserRoles(user.getId(), userDTO.getRoles());
        }

        if (mergeIdentifiers) {
            mergeUserIdentifiers(user, false, userDTO.getIdentifiers(), true);
        }

        if (!StringUtils.isEmpty(userDTO.getPassword())) {
            changeUserPassword(user.getId(), userDTO.getPassword());
        }

        return user;
    }

    @Override
    public List<UserRole> getUserRoles(UUID userId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        return idObjectService.getList(UserRole.class, null, "el.user.id=:userId", params, null, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void mergeUserRoles(UUID userId, Collection<UUID> activeRoles) {
        User user = idObjectService.getObjectById(User.class, userId);
        Set<UUID> currentRoles = new HashSet<>();
        for (UserRole userRole : getUserRoles(userId)) {
            currentRoles.add(userRole.getRole().getId());
        }

        //Delete non-active roles
        String query = "el.user.id=:userId ";
        HashMap<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        if (!activeRoles.isEmpty()) {
            query += "and el.role.id not in (:roles) ";
            params.put("roles", activeRoles);
        }
        idObjectService.delete(UserRole.class, query, params);

        //Add active roles
        for (UUID roleId : activeRoles) {
            if (!currentRoles.contains(roleId)) {
                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(idObjectService.getObjectById(Role.class, roleId));
                idObjectService.save(userRole);
            }
        }
    }

    private void deleteIdentifiersCascade(Collection<UUID> identifierIds) {
        if (identifierIds.isEmpty()) {
            return;
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put("identifierIds", identifierIds);

        idObjectService.delete(Token.class, "el.identifier.id in (:identifierIds)", params);
        idObjectService.delete(UserSession.class, " el.identifier.id in (:identifierIds)", params);
        idObjectService.delete(IncorrectAuthAttempt.class, " el.identifier.id in (:identifierIds)", params);
        idObjectService.delete(Identifier.class, "el.id in (:identifierIds)", params);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void mergeUserIdentifiers(User user, boolean isNewUser, Collection<IdentifierDTO> identifierDTOs, boolean throwExceptionIfAlreadyAttached) throws InvalidIdentifierException {
        List<Identifier> existingIdentifiers = Collections.emptyList();
        if (!isNewUser) {
            Set<UUID> toDelete = new HashSet<>();
            HashMap<String, Object> params = new HashMap<>();
            params.put("userId", user.getId());
            existingIdentifiers = idObjectService.getList(Identifier.class, null, "el.user.id = :userId", params, null, null, null);

            for (Identifier identifier : existingIdentifiers) {
                boolean found = false;
                for (IdentifierDTO dto : identifierDTOs) {
                    if (dto.getId() != null && dto.getId().equals(identifier.getId())) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    toDelete.add(identifier.getId());
                }
            }
            deleteIdentifiersCascade(toDelete);
        }

        for (IdentifierDTO dto : identifierDTOs) {
            dto.setValue(StringUtils.trim(dto.getValue()));

            if (!isIdentifierValid(dto.getIdentifierTypeId(), dto.getValue(), false)) {
                throw new InvalidIdentifierException("Identifier value is not valid: " + dto.getValue());
            }

            Identifier nonCurrentUserIdentifier = findIdentifier(dto.getIdentifierTypeId(), dto.getValue(), false);
            if (nonCurrentUserIdentifier != null) {
                if (nonCurrentUserIdentifier.getUser() == null) {
                    deleteIdentifiersCascade(Collections.singleton(nonCurrentUserIdentifier.getId()));
                }
                else if (nonCurrentUserIdentifier.getUser() != null && nonCurrentUserIdentifier.getUser().equals(user)) {
                    //Nothing to do
                }
                else if (nonCurrentUserIdentifier.getUser() != null && !nonCurrentUserIdentifier.getUser().equals(user)) {
                    if (!nonCurrentUserIdentifier.getVerified()) {
                        deleteIdentifiersCascade(Collections.singleton(nonCurrentUserIdentifier.getId()));
                    }
                    else {
                        throw new InvalidIdentifierException("Identifier is already used: " + nonCurrentUserIdentifier.getId());
                    }
                }
            }

            Identifier identifier = null;
            if (dto.getId() != null) {
                for (Identifier i : existingIdentifiers) {
                    if (dto.getId().equals(i.getId())) {
                        identifier = i;
                        break;
                    }
                }
                if (identifier == null) {
                    throw new InvalidIdentifierException("Identifier of user not found by id: " + dto.getId());
                }
            } else {
                identifier = new Identifier();
                identifier.setUser(user);
            }

            IdentifierType identifierType = ds.get(IdentifierType.class, dto.getIdentifierTypeId());
            identifier.setIdentifierType(identifierType);
            if (isNewUser) {
                identifier.setVerified(identifierType.getAutomaticVerification());
            } else {
                identifier.setVerified(dto.getVerified() != null ? dto.getVerified() : false);
            }
            identifier.setPrimary(dto.getPrimary() != null ? dto.getPrimary() : false);
            identifier.setValue(dto.getValue());
            idObjectService.save(identifier);
        }
    }

    @Override
    public String translateUserSortFieldToNative(String sortFieldInJPAFormat) {
        if (!StringUtils.isEmpty(sortFieldInJPAFormat)) {
            //Т.к. в данном методе запрос используется нативный и требуется сохранить единообразие - транслируем название jpa полей в нативные sql
            if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.id")) {
                sortFieldInJPAFormat = "el.id";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.created")) {
                sortFieldInJPAFormat = "el.created_dt";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.changed")) {
                sortFieldInJPAFormat = "el.changed_dt";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.approved")) {
                sortFieldInJPAFormat = "el.is_approved";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.blocked")) {
                sortFieldInJPAFormat = "el.is_blocked";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.blockedDt")) {
                sortFieldInJPAFormat = "el.blocked_dt";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.blockedByUser")) {
                sortFieldInJPAFormat = "el.blocked_by_user_id";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.lastVisitDt")) {
                sortFieldInJPAFormat = "el.last_visit_dt";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.lastVisitIP")) {
                sortFieldInJPAFormat = "el.last_visit_ip";
            } else if (StringUtils.equalsIgnoreCase(sortFieldInJPAFormat, "el.allowedAddresses")) {
                sortFieldInJPAFormat = "el.allowed_addresses";
            } else if (StringUtils.startsWithIgnoreCase(sortFieldInJPAFormat, "el.fields")) {
                //Nothing to do
            }
        }
        return sortFieldInJPAFormat;
    }

    @Override
    public EntityListResponse<UserDTO> getUsersPaged(String identifierValue, Boolean approved, Boolean blocked, Map<String, String> fields, boolean fetchRoles, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        sortField = translateUserSortFieldToNative(sortField);

        int totalCount = userDao.getUsersCount(identifierValue, approved, blocked, fields);

        EntityListResponse<UserDTO> entityListResponse = new EntityListResponse<UserDTO>(totalCount, count, page, start);

        List<User> items = userDao.getUsers(identifierValue, approved, blocked, fields, sortField, sortDir, entityListResponse.getStartRecord(), count);
        Set<UUID> userIds = new HashSet<>();
        for (User user : items) {
            userIds.add(user.getId());
        }

        List<UserRole> userRoles = Collections.emptyList();
        List<Identifier> identifiers = Collections.emptyList();


        Map<String, Object> params = new HashMap<>();
        params.put("userIds", userIds);
        if (!items.isEmpty()) {
            if (fetchRoles) {
                userRoles = idObjectService.getList(UserRole.class, null, "el.user.id in (:userIds)", params, null, null, null);
            }
            identifiers = idObjectService.getList(Identifier.class, null, "el.user.id in (:userIds) ", params, null, null, null);
        }

        for (User user : items) {
            UserDTO el = UserDTO.prepare(user);

            for (UserRole ur : userRoles) {
                if (ur.getUser().getId().equals(user.getId())) {
                    el.getRoles().add(ur.getRole().getId());
                }
            }
            for (Identifier identifier : identifiers) {
                if (identifier.getUser().getId().equals(user.getId())) {
                    el.getIdentifiers().add(IdentifierDTO.prepare(identifier));
                }
            }

            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    public UserDTO getUser(UUID userId, boolean fetchRoles) throws ObjectNotFoundException {
        User user = idObjectService.getObjectById(User.class, userId);
        if (user == null) {
            throw new ObjectNotFoundException();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        List<Identifier> identifiers = idObjectService.getList(Identifier.class, null, "el.user.id=:userId", params, null, null, null, null);

        UserDTO el = UserDTO.prepare(user);
        for (Identifier identifier : identifiers) {
            el.getIdentifiers().add(IdentifierDTO.prepare(identifier));
        }
        if (fetchRoles) {
            List<UserRole> userRoles = idObjectService.getList(UserRole.class, null, "el.user.id=:userId", params, null, null, null, null);
            for (UserRole ur : userRoles) {
                el.getRoles().add(ur.getRole().getId());
            }
        }
        return el;
    }

    @Override
    public EntityListResponse<RoleDTO> getRolesPaged(String code, String name, boolean fetchGrants, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String fetches = "";
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<String, Object>();

        if (!StringUtils.isEmpty(code)) {
            params.put("code", "%%" + StringUtils.lowerCase(code) + "%%");
            cause += "and lower(el.code) like :code ";
        }
        if (!StringUtils.isEmpty(name)) {
            params.put("name", "%%" + StringUtils.lowerCase(name) + "%%");
            cause += "and lower(el.name) like :name ";
        }

        int totalCount = idObjectService.getCount(Role.class, null, countFetches, cause, params);

        EntityListResponse<RoleDTO> entityListResponse = new EntityListResponse<RoleDTO>(totalCount, count, page, start);

        List<Role> items = idObjectService.getList(Role.class, fetches, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);

        List<RoleGrant> roleGrants = Collections.emptyList();
        if (fetchGrants && !items.isEmpty()) {
            Set<UUID> roleIds = new HashSet<>();
            for (Role r : items) {
                roleIds.add(r.getId());
            }
            Map<String, Object> grantParams = new HashMap<>();
            grantParams.put("roleIds", roleIds);
            roleGrants = idObjectService.getList(RoleGrant.class, null, "el.role.id in (:roleIds)", grantParams, null, null, null, null);
        }

        for (Role e : items) {
            RoleDTO el = RoleDTO.prepare(e);
            for (RoleGrant rg : roleGrants) {
                if (rg.getRole().getId().equals(e.getId())) {
                    el.getGrants().add(rg.getGrant().getId());
                }
            }
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public Role saveRole(RoleDTO dto) throws ObjectNotFoundException {
        Role entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(Role.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new Role();
        }

        if (entity.getId() != null) {
            String query = "el.role.id=:roleId";
            HashMap<String, Object> params = new HashMap<>();
            params.put("roleId", entity.getId());
            idObjectService.delete(RoleGrant.class, query, params);
        }

        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        idObjectService.save(entity);

        for (UUID grantId : dto.getGrants()) {
            RoleGrant rg = new RoleGrant();
            rg.setRole(entity);
            rg.setGrant(idObjectService.getObjectById(Grant.class, grantId));
            idObjectService.save(rg);
        }

        return entity;
    }


    @Override
    public RoleDTO getRole(UUID roleId, boolean fetchGrants) throws ObjectNotFoundException {
        Role entity = idObjectService.getObjectById(Role.class, roleId);
        if (entity == null) {
            throw new ObjectNotFoundException();
        }
        RoleDTO el = RoleDTO.prepare(entity);

        if (fetchGrants) {
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", roleId);
            List<RoleGrant> roleGrants = idObjectService.getList(RoleGrant.class, null, "el.role.id=:roleId", params, null, null, null, null);
            for (RoleGrant rg : roleGrants) {
                if (rg.getRole().getId().equals(roleId)) {
                    el.getGrants().add(rg.getGrant().getId());
                }
            }
        }
        return el;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteRole(UUID roleId) {
        String query = "el.role.id=:roleId";
        HashMap<String, Object> params = new HashMap<>();
        params.put("roleId", roleId);

        idObjectService.delete(RoleGrant.class, query, params);
        idObjectService.delete(Role.class, roleId);
    }

    @Override
    public EntityListResponse<UserSessionDTO> getSessionsPaged(UUID userId, String authIp, Date startDate, Date endDate, boolean enrich, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String fetches = enrich ? "left join fetch el.user" : "";
        String countFetches = "";
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<String, Object>();

        if (userId != null) {
            cause += "and el.user.id=:userId ";
            params.put("userId", userId);
        }

        if (!StringUtils.isEmpty(authIp)) {
            cause += "and el.authIp=:authIp ";
            params.put("authIp", authIp);
        }

        if (startDate != null) {
            cause += "and el.created >= :startDate ";
            params.put("startDate", startDate);
        }

        if (endDate != null) {
            cause += "and el.created <= :endDate ";
            params.put("endDate", endDate);
        }

        int totalCount = idObjectService.getCount(UserSession.class, null, countFetches, cause, params);

        EntityListResponse<UserSessionDTO> entityListResponse = new EntityListResponse<UserSessionDTO>(totalCount, count, page, start);

        List<UserSession> items = idObjectService.getList(UserSession.class, fetches, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);
        for (UserSession e : items) {
            UserSessionDTO el = UserSessionDTO.prepare(e);
            if (enrich) {
                UserSessionDTO.enrich(el, e);
            }
            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeLocale(HttpServletRequest request, AuthorizedUser authorizedUser, String locale) throws IllegalArgumentException {
        Locale l = LocaleUtils.toLocale(locale);
        if (l != null) {
            if (authorizedUser != null) {
                User user = idObjectService.getObjectById(User.class, authorizedUser.getId());
                user.setLocale(locale);
                idObjectService.save(user);
                authorizedUser.setLocale(locale);
            }

            if (request != null) {
                try {
                    request.getSession(false).setAttribute(LocaleFilter.SESSION_ATTRIBUTE_LOCALE, locale);
                } catch (Exception ignored) {
                }
            }

            LocaleHolder.setLocale(l);
        }
    }

    @Override
    public Date getUserPasswordExpirationDate(UUID userId) throws ObjectNotFoundException {
        Passphrase passphrase = getActualPassphrase(userId, DataConstants.PassphraseTypes.USER_PASSWORD.getValue(), userId, false);
        if (passphrase == null) {
            throw new ObjectNotFoundException();
        }

        PassphraseType passphraseType = ds.get(PassphraseType.class, DataConstants.PassphraseTypes.USER_PASSWORD.getValue());
        if (passphraseType.getLifetime() != null && passphraseType.getLifetime() > 0) {
            return new Date(passphrase.getCreated().getTime() + passphraseType.getLifetime());
        }
        return null;
    }

    @Override
    public boolean isIdentifierValid(UUID identifierTypeId, String identifierValue, boolean checkAvailability) {
        if (StringUtils.isEmpty(identifierValue)) {
            return false;
        } else {
            IdentifierType identifierType = ds.get(IdentifierType.class, identifierTypeId);
            if (!StringUtils.isEmpty(identifierType.getValidationRegex())) {
                Pattern p = Pattern.compile(identifierType.getValidationRegex());
                Matcher m = p.matcher(identifierValue);
                if (!m.matches()) {
                    return false;
                }
            }
        }

        if (checkAvailability) {
            Identifier identifier = findIdentifier(identifierTypeId, identifierValue, false);
            if (identifier != null && identifier.getUser() != null && identifier.getVerified()) {
                return false;
            }
        }

        return true;
    }

    public UUID resolveIdentifierTypeId(String identifierValue) throws InvalidIdentifierException {
        //TODO: optimize this get list request
        List<IdentifierType> identifierTypes = idObjectService.getList(IdentifierType.class, null, null, null, "el.resolvePriority ASC", null, null);
        for (IdentifierType identifierType : identifierTypes) {
            if (isIdentifierValid(identifierType.getId(), identifierValue, false)) {
                return identifierType.getId();
            }
        }

        throw new InvalidIdentifierException();
    }

    @Override
    public Identifier findIdentifier(UUID identifierTypeId, String value, boolean enrich) {
        return userDao.findIdentifier(identifierTypeId, value, enrich);
    }

    @Override
    public boolean isPassphraseValueValid(Passphrase passphrase, String value) {
        if (passphrase == null) {
            return false;
        }

        PassphraseType passphraseType = ds.get(PassphraseType.class, passphrase.getPassphraseType().getId());
        if (passphraseType.getPassphraseEncryption().getId().equals(DataConstants.PassphraseEncryptors.OPEN.getValue())) {
            return StringUtils.equals(passphrase.getValue(), value);
        } else if (passphraseType.getPassphraseEncryption().getId().equals(DataConstants.PassphraseEncryptors.SHA1_WITH_SALT.getValue())) {
            return StringUtils.equals(DigestUtils.shaHex(value.concat(passphrase.getSalt())), passphrase.getValue());
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Passphrase getActualVerificationCode(User user, UUID referenceObjectId, UUID passphraseTypeId, boolean createNewIfNotExist) {
        Passphrase passphrase = getActualPassphrase(user.getId(), passphraseTypeId, referenceObjectId, true);
        if (passphrase == null && createNewIfNotExist) {
            PassphraseType passphraseType = ds.get(PassphraseType.class, passphraseTypeId);
            String value = null;
            if (passphraseType.getPassphraseGenerator() != null) {
                if (passphraseType.getPassphraseGenerator().getId().equals(DataConstants.PassphraseGenerators.STATIC_FOUR_ZEROS.getValue())) {
                    value = "0000";
                }
                else if (passphraseType.getPassphraseGenerator().getId().equals(DataConstants.PassphraseGenerators.RANDOM_FOUR_DIGITS.getValue())) {
                    value = String.valueOf(generateCode(9999, 1000));
                }
                else if (passphraseType.getPassphraseGenerator().getId().equals(DataConstants.PassphraseGenerators.RANDOM_SIX_DIGITS.getValue())) {
                    value = String.valueOf(generateCode(999999, 100000));
                }
            }

            passphrase = createPassphrase(user, passphraseType, value, referenceObjectId);
        }

        return passphrase;
    }

    @Override
    public Passphrase getActualPassphrase(UUID userId, UUID passphraseTypeId, UUID referenceObjectId, boolean archiveExpiredPassphrase) {
        PassphraseType passphraseType = ds.get(PassphraseType.class, passphraseTypeId);

        Map<String, Object> params = new HashMap<>();
        params.put("passphraseTypeId", passphraseType.getId());
        params.put("referenceObjectId", referenceObjectId);
        params.put("userId", userId);
        params.put("passphraseStateId", DataConstants.PassphraseStates.ACTUAL.getValue());
        List<Passphrase> passphrases = idObjectService.getList(Passphrase.class, null, "el.user.id=:userId and el.passphraseType.id=:passphraseTypeId and el.passphraseState.id=:passphraseStateId and el.referenceObjectId=:referenceObjectId", params, "el.created DESC", null, 1);
        Passphrase passphrase = null;
        if (!passphrases.isEmpty()) {
            passphrase = passphrases.iterator().next();
            if (archiveExpiredPassphrase && passphraseType.getLifetime() != null && passphraseType.getLifetime() > 0) {
                if ((passphrase.getCreated().getTime() + passphraseType.getLifetime()) < System.currentTimeMillis()) {
                    passphrase.setPassphraseState(ds.get(PassphraseState.class, DataConstants.PassphraseStates.ARCHIVE.getValue()));
                    idObjectService.save(passphrase);
                    passphrase = null;
                }
            }
        }

        return passphrase;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = InvalidPassphraseException.class)
    public Identifier processSignIn(UUID identifierTypeId, String identifierValue, String password, String remoteAddress, boolean trust) throws UserBlockedException, TooManyAttemptsException, NotAllowedIPException, UserNotApprovedException, InvalidIdentifierException {
        identifierValue = StringUtils.trim(identifierValue);

        if (identifierTypeId == null) {
            identifierTypeId = resolveIdentifierTypeId(identifierValue);
        }

        Identifier identifier = findIdentifier(identifierTypeId, identifierValue, true);
        if (identifier != null && identifier.getUser() != null && identifier.getVerified()) {
            IdentifierType identifierType = ds.get(IdentifierType.class, identifier.getIdentifierType().getId());
            if (!identifierType.getSignInAllowed()) {
                throw new InvalidIdentifierException("Sign in is not allowed via this identifier type");
            }
            User user = identifier.getUser();
            long currentTimeMillis = System.currentTimeMillis();
            Date currentDate = new Date(currentTimeMillis);

            if (!user.getApproved()) {
                throw new UserNotApprovedException();
            }
            if (user.getBlocked() != null && user.getBlocked()) {
                throw new UserBlockedException();
            }
            if (!StringUtils.isEmpty(user.getAllowedAddresses())) {
                StringTokenizer stringTokenizer = new StringTokenizer(user.getAllowedAddresses(), " ,");
                boolean inRange = false;
                while (stringTokenizer.hasMoreTokens()) {
                    String token = stringTokenizer.nextToken();
                    if (new SubnetUtils(token).getInfo().isInRange(remoteAddress)) {
                        inRange = true;
                        break;
                    }
                }
                if (!inRange) {
                    throw new NotAllowedIPException();
                }
            }


            if (!StringUtils.isEmpty(user.getAuthScheduleCronExpression())) {
                try {
                    CronExpression cronExpression = new CronExpression(user.getAuthScheduleCronExpression());
                    if (!cronExpression.isSatisfiedBy(currentDate)) {
                        throw new UserBlockedException("User is blocked or not allowed to sign in at this time");
                    }
                } catch (ParseException ignored) {
                }
            }

            Date startDate = new Date(currentTimeMillis - propertyService.getPropertyValueAsInteger("user:block_period"));
            Map<String, Object> params = new HashMap<>();
            params.put("identifierId", identifier.getId());
            params.put("startDate", startDate);
            params.put("endDate", currentDate);
            Integer attemptsToBlock = identifierType.getMaxIncorrectAuthAttempts();
            Integer checkIncorrectLoginAttempts = idObjectService.checkExist(IncorrectAuthAttempt.class, null, "el.identifier.id=:identifierId and el.created >= :startDate and el.created <= :endDate", params, attemptsToBlock);

            if (checkIncorrectLoginAttempts < attemptsToBlock) {
                Passphrase passphrase = getActualPassphrase(user.getId(), DataConstants.PassphraseTypes.USER_PASSWORD.getValue(), user.getId(), true);
                if (trust || isPassphraseValueValid(passphrase, password)) {
                    user.setLastVisitDt(new Date());
                    user.setLastVisitIP(remoteAddress);
                    user = idObjectService.save(user);
                    return identifier;
                } else {
                    IncorrectAuthAttempt incorrectAuthAttempt = new IncorrectAuthAttempt();
                    incorrectAuthAttempt.setIdentifier(identifier);
                    incorrectAuthAttempt.setUser(identifier.getUser());
                    idObjectService.save(incorrectAuthAttempt);

                    throw new InvalidPassphraseException();
                }
            } else {
                throw new TooManyAttemptsException();
            }
        }

        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Passphrase updatePassphrase(User user, String value, UUID passphraseTypeId, UUID referenceObjectId, boolean archiveOtherPassphrases) throws InvalidPassphraseException {
        PassphraseType passphraseType = ds.get(PassphraseType.class, passphraseTypeId);
        if (StringUtils.isEmpty(value)) {
            throw new InvalidPassphraseException();
        } else {
            if (!StringUtils.isEmpty(passphraseType.getValidationRegex())) {
                Pattern p = Pattern.compile(passphraseType.getValidationRegex());
                Matcher m = p.matcher(value);
                if (!m.matches()) {
                    throw new InvalidPassphraseException();
                }
            }
        }

        if (archiveOtherPassphrases) {
            archiveActualPassphrases(user.getId(), passphraseTypeId, referenceObjectId);
        }

        return createPassphrase(user, passphraseType, value, referenceObjectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveActualPassphrases(UUID userId, UUID passphraseTypeId, UUID referenceObjectId) {
        Map<String, Object> params = new HashMap<>();
        params.put("referenceObjectId", referenceObjectId);
        params.put("passphraseTypeId", passphraseTypeId);
        params.put("userId", userId);
        params.put("passphraseStateId", DataConstants.PassphraseStates.ACTUAL.getValue());
        List<Passphrase> passphrases = idObjectService.getList(Passphrase.class, null, "el.user.id=:userId and el.passphraseType.id=:passphraseTypeId and el.referenceObjectId=:referenceObjectId and el.passphraseState.id=:passphraseStateId", params, "el.created DESC", null, null);
        PassphraseState archiveState = ds.get(PassphraseState.class, DataConstants.PassphraseStates.ARCHIVE.getValue());
        for (Passphrase passphrase : passphrases) {
            passphrase.setPassphraseState(archiveState);
            idObjectService.save(passphrase);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archivePassphrase(Passphrase passphrase) {
        PassphraseState archiveState = ds.get(PassphraseState.class, DataConstants.PassphraseStates.ARCHIVE.getValue());
        passphrase.setPassphraseState(archiveState);
        idObjectService.save(passphrase);
    }

    private Passphrase createPassphrase(User user, PassphraseType passphraseType, String value, UUID referenceObjectId) {
        Passphrase passphrase = new Passphrase();
        passphrase.setUser(user);
        passphrase.setPassphraseState(ds.get(PassphraseState.class, DataConstants.PassphraseStates.ACTUAL.getValue()));
        passphrase.setPassphraseType(passphraseType);
        passphrase.setReferenceObjectId(referenceObjectId);
        if (passphraseType.getPassphraseEncryption().getId().equals(DataConstants.PassphraseEncryptors.OPEN.getValue())) {
            passphrase.setValue(value);
        } else if (passphraseType.getPassphraseEncryption().getId().equals(DataConstants.PassphraseEncryptors.SHA1_WITH_SALT.getValue())) {
            passphrase.setSalt(UserServiceImpl.generateCryptographicSalt());
            passphrase.setValue(DigestUtils.shaHex(value.concat(passphrase.getSalt())));
        }
        return idObjectService.save(passphrase);
    }

    private static String generateCryptographicSalt() {
        Random random = new Random(System.currentTimeMillis());
        return DigestUtils.md5Hex(String.valueOf(random.nextLong()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean processIdentifierVerificationViaVerificationCode(UUID identifierTypeId, String identifierValue, String verificationCode) {
        Identifier identifier = findIdentifier(identifierTypeId, identifierValue, false);
        if (identifier == null || identifier.getVerified()) {
            return true;
        }

        Passphrase passphrase = getActualVerificationCode(identifier.getUser(), identifier.getId(), DataConstants.PassphraseTypes.IDENTIFIER_VERIFICATION_CODE.getValue(), false);
        if (isPassphraseValueValid(passphrase, verificationCode)) {
            identifier.setVerified(true);
            idObjectService.save(identifier);

            passphrase.setPassphraseState(ds.get(PassphraseState.class, DataConstants.PassphraseStates.ARCHIVE.getValue()));
            idObjectService.save(passphrase);

            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = InvalidPassphraseException.class)
    public Token establishToken(AuthRequestDTO authRequestDTO, String remoteAddress, boolean trust) throws UserBlockedException, TooManyAttemptsException, NotAllowedIPException, UserNotApprovedException, InvalidIdentifierException {
        Identifier identifier = processSignIn(authRequestDTO.getIdentifierTypeId(), authRequestDTO.getIdentifierValue(), authRequestDTO.getPassword(), remoteAddress, trust);
        if (identifier != null) {
            Token token = new Token();
            token.setUser(identifier.getUser());
            token.setLastRequest(new Date());
            token.setActive(true);
            token.setIdentifier(identifier);
            token = idObjectService.save(token);

            return token;
        } else {
            return null;
        }
    }

    @Override
    @Transactional
    public void updateTokenLastRequestDate(Token newToken) {
        newToken.setLastRequest(new Date());
        idObjectService.save(newToken);
    }

    @Override
    @Transactional
    public void deactivateToken(TokenDTO tokenDTO) {
        idObjectService.updateFieldValue(Token.class, tokenDTO.getToken(), "active", false);
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional
    public void blockExpiredUsers() {
        Date currentTime = new Date();

        Map<String, Object> param = new HashMap<>();
        param.put("currentTime", currentTime);
        List<User> users = idObjectService.getList(User.class, null, "el.blocked=false and el.blockAfterDt <= :currentTime", param, null, null, null);
        for (User user : users) {
            user.setBlocked(true);
            user.setBlockedDt(currentTime);
            idObjectService.save(user);
        }
    }

    @Override
    public EntityListResponse<PassphraseTypeDTO> getPassphraseTypePaged(String name, boolean enrich, Integer count, Integer page, Integer start, String sortField, String sortDir) {
        String cause = "1=1 ";
        HashMap<String, Object> params = new HashMap<>();
        if (!StringUtils.isEmpty(name)) {
            params.put("name", "%%" + StringUtils.lowerCase(name) + "%%");
            cause += " and lower(el.name) like :name";
        }

        int totalCount = idObjectService.getCount(PassphraseType.class, null, null, cause, params);

        EntityListResponse<PassphraseTypeDTO> entityListResponse = new EntityListResponse<PassphraseTypeDTO>(totalCount, count, page, start);

        List<PassphraseType> items = idObjectService.getList(PassphraseType.class, null, cause, params, sortField, sortDir, entityListResponse.getStartRecord(), count);
        for (PassphraseType e : items) {
            PassphraseTypeDTO el = PassphraseTypeDTO.prepare(e);
            if (enrich) {
                PassphraseTypeDTO.enrich(el, e);
            }

            entityListResponse.addData(el);
        }

        return entityListResponse;
    }

    @Override
    public PassphraseTypeDTO getPassphraseType(UUID id) throws ObjectNotFoundException {
        PassphraseType passphraseType = idObjectService.getObjectById(PassphraseType.class, "left join fetch el.passphraseEncryption left join fetch el.passphraseGenerator", id);
        if (passphraseType == null) {
            throw new ObjectNotFoundException();
        }

        return PassphraseTypeDTO.prepare(passphraseType);
    }

    @Override
    @Transactional
    public PassphraseType savePassphraseType(PassphraseTypeDTO dto) throws ObjectNotFoundException {
        PassphraseType entity;
        if (dto.getId() != null) {
            entity = idObjectService.getObjectById(PassphraseType.class, dto.getId());
            if (entity == null) {
                throw new ObjectNotFoundException();
            }
        } else {
            entity = new PassphraseType();
        }

        entity.setName(dto.getName());
        entity.setLifetime(dto.getLifetime());
        entity.setValidationRegex(dto.getValidationRegex());
        entity.setPassphraseEncryption(ds.get(PassphraseEncryption.class, dto.getPassphraseEncryptionId()));
        entity.setPassphraseGenerator(ds.get(PassphraseGenerator.class, dto.getPassphraseGeneratorId()));

        return idObjectService.save(entity);
    }

    @Override
    @Transactional
    public void deletePassphraseType(UUID id) {
        idObjectService.delete(PassphraseType.class, id);
    }
}
