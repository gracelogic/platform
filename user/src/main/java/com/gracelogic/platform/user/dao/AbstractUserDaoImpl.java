package com.gracelogic.platform.user.dao;

import com.gracelogic.platform.db.dao.BaseDao;
import com.gracelogic.platform.user.model.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;

public abstract class AbstractUserDaoImpl extends BaseDao implements UserDao {
    private static Logger logger = Logger.getLogger(AbstractUserDaoImpl.class);

    @Override
    public Identifier findIdentifier(UUID identifierTypeId, String identifierValue, boolean caseIndependent, boolean enrich) {
        String query = "select el from Identifier el " +
                (enrich ? "left join fetch el.user user " : " ") +
                "where el.identifierType.id=:identifierTypeId";

        if (caseIndependent) {
            query += " and lower(el.value)=:val";
        }
        else {
            query += " and el.value=:val";
        }

        query += " order by el.verified desc";


        try {
            List<Identifier> identifiers = getEntityManager().createQuery(query, Identifier.class)
                    .setParameter("identifierTypeId", identifierTypeId)
                    .setParameter("val", caseIndependent ? StringUtils.lowerCase(identifierValue) : identifierValue).setMaxResults(1).getResultList();
            if (!identifiers.isEmpty()) {
                return identifiers.iterator().next();
            }
            else {
                return null;
            }
        } catch (Exception e) {
            logger.debug(String.format("Failed to get identifier by value: %s", identifierValue), e);
        }
        return null;
    }
}
