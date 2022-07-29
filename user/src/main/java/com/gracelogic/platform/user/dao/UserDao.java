package com.gracelogic.platform.user.dao;


import com.gracelogic.platform.user.model.Identifier;
import com.gracelogic.platform.user.model.User;

import java.util.*;

public interface UserDao {
    Integer getUsersCount(String identifierValue, Boolean approved, Boolean blocked, Map<String, String> fields);

    List<User> getUsers(String identifierValue, Boolean approved, Boolean blocked, Map<String, String> fields, String sortField, String sortDir, Integer startRecord, Integer recordsOnPage);

    List<Object[]> getLastActiveUsersSessions();

    Identifier findIdentifier(UUID identifierTypeId, String identifierValue, boolean caseIndependent, boolean enrich);
}
