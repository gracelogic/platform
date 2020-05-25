package com.gracelogic.platform.user.dto;

public class ValidateIdentifierResponseDTO {
    private Boolean valid;

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public ValidateIdentifierResponseDTO(Boolean valid) {
        this.valid = valid;
    }
}
