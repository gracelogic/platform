package com.gracelogic.platform.user.api;

import com.gracelogic.platform.localization.service.LocaleHolder;
import com.gracelogic.platform.user.Path;
import com.gracelogic.platform.user.PlatformRole;
import com.gracelogic.platform.user.dto.IdentifierRequestDTO;
import com.gracelogic.platform.user.dto.ValidateIdentifierResponseDTO;
import com.gracelogic.platform.user.dto.VerifyIdentifierRequestDTO;
import com.gracelogic.platform.user.service.UserService;
import com.gracelogic.platform.web.dto.EmptyResponse;
import com.gracelogic.platform.web.dto.ErrorResponse;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping(value = Path.API_IDENTIFIER)
@Secured(PlatformRole.ANONYMOUS)
@Api(value = Path.API_IDENTIFIER, tags = {"Identifier API"},
        authorizations = @Authorization(value = "MybasicAuth"))
public class IdentifierApi extends AbstractAuthorizedController {

    @Autowired
    private UserService userService;

    @Autowired
    @Qualifier("userMessageSource")
    private ResourceBundleMessageSource messageSource;

    @ApiOperation(
            value = "validate",
            notes = "Validate identifier",
            response = ResponseEntity.class
    )
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 400, message = "Invalid phone"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity validate(@ApiParam(name = "dto", value = "dto") @RequestBody IdentifierRequestDTO dto,
                                   @ApiParam(name = "checkAvailability", value = "checkAvailability") @RequestParam(value = "checkAvailability", required = false, defaultValue = "false") Boolean checkAvailability
                                   ) {
        Boolean valid = userService.isIdentifierValid(dto.getIdentifierTypeId(), dto.getIdentifierValue(), checkAvailability);
        return new ResponseEntity<ValidateIdentifierResponseDTO>(new ValidateIdentifierResponseDTO(valid), HttpStatus.OK);
    }

    @ApiOperation(
            value = "verifyIdentifier",
            notes = "Verify identifier",
            response = ResponseEntity.class
    )
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 400, message = "Invalid code"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @RequestMapping(value = "/verify", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity verify(@ApiParam(name = "dto", value = "dto")
                                 @RequestBody VerifyIdentifierRequestDTO dto) {
        if (userService.processIdentifierVerificationViaVerificationCode(dto.getIdentifierTypeId(), dto.getIdentifierValue(), dto.getVerificationCode())) {
            return new ResponseEntity<EmptyResponse>(EmptyResponse.getInstance(), HttpStatus.OK);
        } else {
            return new ResponseEntity<ErrorResponse>(new ErrorResponse("common.AUTH_CODE_IS_INCORRECT", messageSource.getMessage("common.AUTH_CODE_IS_INCORRECT", null, LocaleHolder.getLocale())), HttpStatus.BAD_REQUEST);
        }
    }

    //TODO: Написать методы по добавлению/редактированию/удалению/получению списка Identifiers


}
