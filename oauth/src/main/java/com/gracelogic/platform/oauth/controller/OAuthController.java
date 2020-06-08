package com.gracelogic.platform.oauth.controller;

import com.gracelogic.platform.oauth.DataConstants;
import com.gracelogic.platform.oauth.Path;
import com.gracelogic.platform.oauth.service.OAuthServiceProvider;
import com.gracelogic.platform.property.service.PropertyService;
import com.gracelogic.platform.user.api.AbstractAuthorizedController;
import com.gracelogic.platform.user.dto.AuthorizedUser;
import com.gracelogic.platform.user.model.User;
import com.gracelogic.platform.user.model.UserSession;
import com.gracelogic.platform.user.security.SessionBasedAuthentication;
import com.gracelogic.platform.user.service.UserService;
import com.gracelogic.platform.web.ServletUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping(value = Path.OAUTH)
public class OAuthController extends AbstractAuthorizedController {
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
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private PropertyService propertyService;

    private static Logger logger = Logger.getLogger(OAuthController.class);

    @RequestMapping(value = "/{authProvider}", method = RequestMethod.GET)
    public void process(HttpServletRequest request,
                        HttpServletResponse response,
                        @PathVariable(value = "authProvider") String authProvider,
                        @RequestParam(value = "code", required = false) String code,
                        @RequestParam(value = "fwd", required = false) String fwd) throws IOException {

        logger.info("OAUTH request");
        logger.info("provider: " + authProvider);
        logger.info("code: " + code);


        Boolean codeControllerDisabled = propertyService.getPropertyValueAsBoolean("oauth:code_controller_disabled");
        if (codeControllerDisabled != null && codeControllerDisabled) {
            response.sendError(200);
            return;
        }

        if (StringUtils.isEmpty(code)) {
            response.sendRedirect(propertyService.getPropertyValue("oauth:redirect_fail_url"));
            return;
        }



        User user = null;

        if (authProvider.equalsIgnoreCase(DataConstants.OAuthProviders.VK.name())) {
            try {
                String additionalParameters = null;
                if (fwd != null) {
                    additionalParameters = "?fwd=" + fwd;
                }
                String requestUri = vk.buildRedirectUri(additionalParameters);
                logger.info("REQUEST_URI: " + requestUri);

                user = vk.processAuthorization(code, null, requestUri);
            } catch (Exception e) {
                logger.error("Failed to process user via vk", e);
            }
        } else if (authProvider.equalsIgnoreCase(DataConstants.OAuthProviders.OK.name())) {
            try {
                String additionalParameters = null;
                if (fwd != null) {
                    additionalParameters = "?fwd=" + fwd;
                }
                String requestUri = ok.buildRedirectUri(additionalParameters);
                logger.info("REQUEST_URI: " + requestUri);

                user = ok.processAuthorization(code, null, requestUri);
            } catch (Exception e) {
                logger.error("Failed to process user via ok", e);
            }
        } else if (authProvider.equalsIgnoreCase(DataConstants.OAuthProviders.INSTAGRAM.name())) {
            try {
                String additionalParameters = null;
                if (fwd != null) {
                    additionalParameters = "?fwd=" + fwd;
                }
                String requestUri = instagram.buildRedirectUri(additionalParameters);
                logger.info("REQUEST_URI: " + requestUri);

                user = instagram.processAuthorization(code, null, requestUri);
            } catch (Exception e) {
                logger.error("Failed to process user via instagram", e);
            }
        } else if (authProvider.equalsIgnoreCase(DataConstants.OAuthProviders.FACEBOOK.name())) {
            try {
                String additionalParameters = null;
                if (fwd != null) {
                    additionalParameters = "?fwd=" + fwd;
                }
                String requestUri = facebook.buildRedirectUri(additionalParameters);
                logger.info("REQUEST_URI: " + requestUri);

                user = facebook.processAuthorization(code, null, requestUri);
            } catch (Exception e) {
                logger.error("Failed to process user via facebook", e);
            }
        } else if (authProvider.equalsIgnoreCase(DataConstants.OAuthProviders.GOOGLE.name())) {
            try {
                String additionalParameters = null;
                if (fwd != null) {
                    additionalParameters = "?fwd=" + fwd;
                }
                String requestUri = google.buildRedirectUri(additionalParameters);
                logger.info("REQUEST_URI: " + requestUri);

                user = google.processAuthorization(code, null, requestUri);
            } catch (Exception e) {
                logger.error("Failed to process user via google", e);
            }
        } else if (authProvider.equalsIgnoreCase(DataConstants.OAuthProviders.LINKEDIN.name())) {
            logger.info("LINKEDIN eq");
            try {
                String additionalParameters = null;
                if (fwd != null) {
                    additionalParameters = "?fwd=" + fwd;
                }
                String requestUri = linkedin.buildRedirectUri(additionalParameters);
                logger.info("REQUEST_URI: " + requestUri);

                user = linkedin.processAuthorization(code, null, requestUri);
            } catch (Exception e) {
                logger.error("Failed to process user via linkedin", e);
            }
        } else if (authProvider.equalsIgnoreCase(DataConstants.OAuthProviders.ESIA.name())) {
            try {
                String additionalParameters = null;
                if (fwd != null) {
                    additionalParameters = "?fwd=" + fwd;
                }
                String requestUri = esia.buildRedirectUri(additionalParameters);
                logger.info("REQUEST_URI: " + requestUri);

                user = esia.processAuthorization(code, null, requestUri);
            } catch (Exception e) {
                logger.error("Failed to process user via esia", e);
            }
        } else {
            try {
                response.sendRedirect(propertyService.getPropertyValue("oauth:redirect_fail_url"));
                return;
            } catch (Exception ignored) {
            }
        }

        if (user == null) {
            response.sendRedirect(propertyService.getPropertyValue("oauth:redirect_fail_url"));
            return;
        }

        Exception exception = null;

        //login here
        SessionBasedAuthentication authentication = null;
        try {
            authentication = (SessionBasedAuthentication) authenticationManager.authenticate(
                    new SessionBasedAuthentication(user.getId(), null, ServletUtils.getRemoteAddress(request), com.gracelogic.platform.user.service.DataConstants.IdentifierTypes.USER_ID.getValue(), true)
            );
        } catch (Exception e) {
            exception = e;
            logger.error("Failed to authenticate oauth user", e);
        }

        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (authentication.isAuthenticated()) {
                if (authentication.getDetails() instanceof AuthorizedUser) {
                    AuthorizedUser authorizedUser = ((AuthorizedUser) authentication.getDetails());

                    try {
                        UserSession userSession = userService.updateSessionInfo(request.getSession(), authentication, request.getHeader("User-Agent"), false);
                        if (userSession != null) {
                            authorizedUser.setUserSessionId(userSession.getId());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (exception != null) {
            response.sendRedirect(propertyService.getPropertyValue("oauth:redirect_fail_url"));
            return;
        }

        if (StringUtils.isEmpty(fwd)) {
            fwd = propertyService.getPropertyValue("oauth:redirect_success_url");
        }

        response.sendRedirect(fwd);
    }
}
