package com.gracelogic.platform.user.service;


import com.gracelogic.platform.notification.exception.SendingException;
import com.gracelogic.platform.user.exception.IllegalParameterException;
import com.gracelogic.platform.user.model.AuthCode;
import com.gracelogic.platform.user.model.User;
import com.gracelogic.platform.user.security.AuthenticationToken;

import javax.servlet.http.HttpSession;
import java.util.UUID;

/**
 * Author: Igor Parkhomenko
 * Date: 23.07.13
 * Time: 13:09
 */
public interface UserService {
    User getUserByField(String fieldName, String fieldValue);

    User login(String login, String loginField, String password, String remoteAddress);

    void changeUserPassword(UUID userId, String newPassword);

    boolean checkPhone(String phone);

    boolean checkEmail(String email);

    boolean verifyLogin(UUID userId, String loginType, String code);

    void updateSessionInfo(HttpSession session, AuthenticationToken authenticationToken, String userAgent, boolean isDestroying);

    void sendRepairCode(String login, String loginType) throws IllegalParameterException, SendingException;

    void changePassword(String login, String loginType, String code, String newPassword) throws IllegalParameterException;

    void sendEmailVerification(UUID userId);

    String getUserSetting(UUID userId, String key);

    void updateUserSetting(UUID userId, String key, String value);

    AuthCode getActualCode(UUID userId, UUID codeTypeId, boolean invalidateImmediately);

    void invalidateCodes(UUID userId, UUID codeTypeId);

    boolean isActualCodeAvailable(UUID userId, UUID codeTypeId);
}
