package com.gracelogic.platform.db.dto;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class DateFormatConstants {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    public static final ThreadLocal<DateFormat> DEFAULT_DATE_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setTimeZone(UTC);
            return format;
        }
    };
}
