package com.gracelogic.platform.localization.service;

import org.apache.commons.lang3.LocaleUtils;

import java.util.Locale;

public class LocaleHolder {
    private static ThreadLocal<Locale> locale = new ThreadLocal<Locale>();
    public static Locale defaultLocale = LocaleUtils.toLocale("en_US");

    public static Locale getLocale() {
        if (locale.get() == null) {
            return defaultLocale;
        }
        else {
            return locale.get();
        }
    }

    public static void setLocale(Locale l) {
        locale.set(l);
    }

    public static void setLocale(String l) {
        try {
            locale.set(LocaleUtils.toLocale(l));
        }
        catch (Exception ignored) {}
    }
}
