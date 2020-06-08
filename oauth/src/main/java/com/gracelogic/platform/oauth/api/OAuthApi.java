package com.gracelogic.platform.oauth.api;

import com.gracelogic.platform.db.exception.ObjectNotFoundException;
import com.gracelogic.platform.localization.service.LocaleHolder;
import com.gracelogic.platform.oauth.Path;
import com.gracelogic.platform.oauth.dto.AuthProviderDTO;
import com.gracelogic.platform.oauth.dto.TokenByCodeRequestDTO;
import com.gracelogic.platform.oauth.service.OAuthService;
import com.gracelogic.platform.user.PlatformRole;
import com.gracelogic.platform.user.api.AbstractAuthorizedController;
import com.gracelogic.platform.user.dto.AuthorizedUser;
import com.gracelogic.platform.user.dto.IdentifierDTO;
import com.gracelogic.platform.user.dto.TokenDTO;
import com.gracelogic.platform.user.exception.*;
import com.gracelogic.platform.user.model.Token;
import com.gracelogic.platform.user.model.User;
import com.gracelogic.platform.user.service.UserLifecycleService;
import com.gracelogic.platform.web.ServletUtils;
import com.gracelogic.platform.web.dto.ErrorResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;


@Controller
@Secured(PlatformRole.ANONYMOUS)
@RequestMapping(value = Path.API_OAUTH)
@Api(value = Path.API_OAUTH, tags = {"OAuth API"})
public class OAuthApi extends AbstractAuthorizedController {
    @Autowired
    private OAuthService oAuthService;

    @Autowired
    @Qualifier("userMessageSource")
    private ResourceBundleMessageSource userMessageSource;

    @Autowired
    @Qualifier("dbMessageSource")
    private ResourceBundleMessageSource dbMessageSource;

    @Autowired
    private UserLifecycleService lifecycleService;

    @ApiOperation(
            value = "providers",
            notes = "Return all available oauth providers",
            response = ResponseEntity.class
    )
    @RequestMapping(value = "providers", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity providers() {
        List<AuthProviderDTO> providers = oAuthService.getAuthProviders();
        return new ResponseEntity<List<AuthProviderDTO>>(providers, HttpStatus.OK);
    }

    @ApiOperation(
            value = "tokenByCode",
            notes = "Token by code",
            response = ResponseEntity.class
    )
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Invalid credentials"),
            @ApiResponse(code = 423, message = "User blocked"),
            @ApiResponse(code = 429, message = "Too many attempts"),
            @ApiResponse(code = 422, message = "Not activated"),
            @ApiResponse(code = 510, message = "Not allowed IP"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @RequestMapping(value = "/token-by-code", method = RequestMethod.POST)
    public ResponseEntity tokenByCode(HttpServletRequest request, HttpServletResponse response, @RequestBody TokenByCodeRequestDTO dto) {
        try {
            Token token = oAuthService.tokenByCode(dto.getAuthProviderId(), dto.getCode(), dto.getAccessToken(), ServletUtils.getRemoteAddress(request));
            if (token != null) {
                User user = token.getIdentifier().getUser();
                AuthorizedUser authorizedUser = AuthorizedUser.prepare(user);
                authorizedUser.setSignInIdentifier(IdentifierDTO.prepare(token.getIdentifier()));
                lifecycleService.signIn(authorizedUser);
                return new ResponseEntity<TokenDTO>(new TokenDTO(token.getId()), HttpStatus.OK);
            } else {
                throw new InvalidIdentifierException();
            }
        } catch (InvalidPassphraseException e) {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("signIn.INVALID_PASSPHRASE", userMessageSource.getMessage("signIn.INVALID_PASSPHRASE", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        } catch (TooManyAttemptsException e) {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("signIn.TOO_MANY_ATTEMPTS", userMessageSource.getMessage("signIn.TOO_MANY_ATTEMPTS", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        } catch (NotAllowedIPException e) {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("signIn.NOT_ALLOWED_ID", userMessageSource.getMessage("signIn.NOT_ALLOWED_ID", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        } catch (UserNotApprovedException e) {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("signIn.USER_NOT_APPROVED", userMessageSource.getMessage("signIn.USER_NOT_APPROVED", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        } catch (InvalidIdentifierException e) {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("signIn.INVALID_IDENTIFIER", userMessageSource.getMessage("signIn.INVALID_IDENTIFIER", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        } catch (UserBlockedException e) {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("signIn.USER_BLOCKED", userMessageSource.getMessage("signIn.USER_BLOCKED", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        } catch (ObjectNotFoundException e) {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("db.NOT_FOUND", dbMessageSource.getMessage("db.NOT_FOUND", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        }
    }
}
