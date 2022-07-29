package com.gracelogic.platform.user.model;

import com.gracelogic.platform.db.JPAProperties;
import com.gracelogic.platform.db.model.IdObject;
import com.gracelogic.platform.dictionary.model.Dictionary;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = JPAProperties.TABLE_PREFIX + "IDENTIFIER_TYPE")
public class IdentifierType extends IdObject<UUID> implements Dictionary {
    @Id
    @Access(AccessType.PROPERTY)
    @Column(name = ID)
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @org.hibernate.annotations.Type(type = "com.gracelogic.platform.db.type.UUIDCustomType")
    private UUID id;

    @Column(name = CREATED, nullable = false)
    private Date created;

    @Version
    @Column(name = CHANGED, nullable = false)
    private Date changed;

    @Column(name = NAME, nullable = false)
    private String name;

    @Column(name = SORT_ORDER, nullable = true)
    private Integer sortOrder;

    @Column(name = "IS_SIGN_IN_ALLOWED", nullable = false)
    private Boolean signInAllowed;

    @Column(name = "IS_AUTOMATIC_VERIFICATION", nullable = false)
    private Boolean automaticVerification;

    @Column(name = "MAX_INCORRECT_AUTH_ATTEMPTS", nullable = true)
    private Integer maxIncorrectAuthAttempts;

    @Column(name = "VALIDATION_REGEX", nullable = true)
    private String validationRegex;

    @Column(name = "IS_CASE_INDEPENDENT", nullable = false)
    private Boolean caseIndependent;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public Date getChanged() {
        return changed;
    }

    @Override
    public void setChanged(Date changed) {
        this.changed = changed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getSignInAllowed() {
        return signInAllowed;
    }

    public void setSignInAllowed(Boolean signInAllowed) {
        this.signInAllowed = signInAllowed;
    }

    public Integer getMaxIncorrectAuthAttempts() {
        return maxIncorrectAuthAttempts;
    }

    public void setMaxIncorrectAuthAttempts(Integer maxIncorrectAuthAttempts) {
        this.maxIncorrectAuthAttempts = maxIncorrectAuthAttempts;
    }

    public String getValidationRegex() {
        return validationRegex;
    }

    public void setValidationRegex(String validationRegex) {
        this.validationRegex = validationRegex;
    }

    public Boolean getAutomaticVerification() {
        return automaticVerification;
    }

    public void setAutomaticVerification(Boolean automaticVerification) {
        this.automaticVerification = automaticVerification;
    }

    @Override
    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String getCode() {
        return null;
    }

    public Boolean getCaseIndependent() {
        return caseIndependent;
    }

    public void setCaseIndependent(Boolean caseIndependent) {
        this.caseIndependent = caseIndependent;
    }
}
