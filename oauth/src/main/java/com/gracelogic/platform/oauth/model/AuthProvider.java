package com.gracelogic.platform.oauth.model;

import com.gracelogic.platform.db.JPAProperties;
import com.gracelogic.platform.db.model.IdObject;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = JPAProperties.TABLE_PREFIX + "AUTH_PROVIDER")
public class AuthProvider extends IdObject<UUID> {
    @Id
    @Column(name = ID)
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @org.hibernate.annotations.Type(type = "com.gracelogic.platform.db.type.UUIDCustomType")
    @Access(AccessType.PROPERTY)
    private UUID id;

    @Column(name = CREATED, nullable = false)
    private Date created;

    @Version
    @Column(name = CHANGED, nullable = false)
    private Date changed;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "SORT_ORDER", nullable = true)
    private Integer sortOrder;

    @Column(name = "ACCESS_TOKEN_ENDPOINT", nullable = true)
    private String accessTokenEndpoint;

    @Column(name = "INFO_ENDPOINT", nullable = true)
    private String infoEndpoint;

    @Column(name = "CLIENT_ID", nullable = false)
    private String clientId;

    @Column(name = "CLIENT_SECRET", nullable = false)
    private String clientSecret;

    @Column(name = "CLIENT_PUBLIC_KEY", nullable = true)
    private String clientPublicKey;

    @Column(name = "IMPORT_EMAIL", nullable = false)
    private Boolean importEmail;

    @Column(name = "IMPORT_PHONR", nullable = false)
    private Boolean importPhone;

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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public String getAccessTokenEndpoint() {
        return accessTokenEndpoint;
    }

    public void setAccessTokenEndpoint(String accessTokenEndpoint) {
        this.accessTokenEndpoint = accessTokenEndpoint;
    }

    public String getInfoEndpoint() {
        return infoEndpoint;
    }

    public void setInfoEndpoint(String infoEndpoint) {
        this.infoEndpoint = infoEndpoint;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientPublicKey() {
        return clientPublicKey;
    }

    public void setClientPublicKey(String clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getImportEmail() {
        return importEmail;
    }

    public void setImportEmail(Boolean importEmail) {
        this.importEmail = importEmail;
    }

    public Boolean getImportPhone() {
        return importPhone;
    }

    public void setImportPhone(Boolean importPhone) {
        this.importPhone = importPhone;
    }
}
