package com.gracelogic.platform.market.model;

import com.gracelogic.platform.account.model.Account;
import com.gracelogic.platform.account.model.Currency;
import com.gracelogic.platform.db.JPAProperties;
import com.gracelogic.platform.db.model.IdObject;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;
import java.util.UUID;

/**
 * MerchantAccount - таблица, заполняемая разработчиком проекта.
 * С помощью нее определяется, с какого лицевого счета покупателя нужно перевести средства на соотв. лицевой счет продавца.
 * Например, с рублевого Account средства переводятся на рублевый Account продавца.
 *
 * С помощью этой таблицы также можно посчитать, сколько всего средств было заработано.
 */
@Entity
@Table(name = JPAProperties.TABLE_PREFIX + "MERCHANT_ACCOUNT")
public class MerchantAccount extends IdObject<UUID> {
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private Account account;

    /**
     * Идентификационный номер налогоплательщика (ИНН)
     */
    @Column(name = "INN", nullable = true)
    private String inn;

    /**
     * Код причины постановки на учет (КПП)
     */
    @Column(name = "KPP", nullable = true)
    private String kpp;

    /**
     * Банковский идентификационный код, БИК
     */
    @Column(name = "BIK", nullable = true)
    private String bik;

    /**
     * Рсч - это?
     */
    @Column(name = "RS", nullable = true)
    private String rs;



    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public Date getChanged() {
        return changed;
    }

    @Override
    public void setChanged(Date changed) {
        this.changed = changed;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public void setCreated(Date created) {
        this.created = created;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }
}
