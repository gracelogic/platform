package com.gracelogic.platform.db.type;

import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.PostgresUUIDType;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.UUIDTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

import java.io.IOException;
import java.util.Properties;

public class UUIDCustomType extends AbstractSingleColumnStandardBasicType {

    private static final long serialVersionUID = 902830399800029445L;

    private static final SqlTypeDescriptor SQL_DESCRIPTOR;
    private static final JavaTypeDescriptor TYPE_DESCRIPTOR;

    public static String DIALECT = "postgres";

    static {
        String dialect = null;
        Properties properties = new Properties();
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            properties.load(loader.getResourceAsStream("/db.properties"));
            dialect = properties.getProperty("dialect");
        } catch (Exception ignored) {
        }

        if (dialect == null) {
            dialect = DIALECT;
        }
        if (dialect.equals("postgres")) {
            SQL_DESCRIPTOR = PostgresUUIDType.PostgresUUIDSqlTypeDescriptor.INSTANCE;
            System.err.println("UUID for: PostgreSQL");
        } else if (dialect.equals("mssql")) {
            SQL_DESCRIPTOR = VarcharTypeDescriptor.INSTANCE;
            System.err.println("UUID for: MSSQL");
        } else {
            throw new UnsupportedOperationException("Unsupported database!");
        }

        TYPE_DESCRIPTOR = UUIDTypeDescriptor.INSTANCE;
    }

    public UUIDCustomType() {
        super(SQL_DESCRIPTOR, TYPE_DESCRIPTOR);
    }

    @Override
    public String getName() {
        return "com.gracelogic.platform.db.type.UUIDCustomType";
    }
}