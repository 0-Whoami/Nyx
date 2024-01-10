package com.termux.shared.android;

import android.content.Context;
import android.content.pm.PackageManager;

import com.termux.shared.reflection.ReflectionUtils;

import java.lang.reflect.Method;

public class UserUtils {


    /**
     * Get the user name for user id with a call to {@link #getNameForUidFromPackageManager(Context, int)}
     * and if that fails, then a call to {@link #getNameForUidFromLibcore(int)}.
     *
     * @param context The {@link Context} for operations.
     * @param uid     The user id.
     * @return Returns the user name if found, otherwise {@code null}.
     */

    public static String getNameForUid(Context context, int uid) {
        String name = getNameForUidFromPackageManager(context, uid);
        if (name == null)
            name = getNameForUidFromLibcore(uid);
        return name;
    }

    /**
     * Get the user name for user id with a call to {@link PackageManager#getNameForUid(int)}.
     * <p>
     * This will not return user names for non app user id like for root user 0, use {@link #getNameForUidFromLibcore(int)}
     * to get those.
     * <p><a href="
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/content/pm/PackageManager.jav</a>a;l=5556<a href="
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ApplicationPackageManager.jav</a>a;l=1028<a href="
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java</a>;l=10293
     *
     * @param context The {@link Context} for operations.
     * @param uid     The user id.
     * @return Returns the user name if found, otherwise {@code null}.
     */

    private static String getNameForUidFromPackageManager(Context context, int uid) {
        if (uid < 0)
            return null;
        try {
            String name = context.getPackageManager().getNameForUid(uid);
            if (name != null && name.endsWith(":" + uid))
                // Remove ":<uid>" suffix
                name = name.replaceAll(":" + uid + "$", "");
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the user name for user id with a call to `Libcore.os.getpwuid()`.
     * <p>
     * This will return user names for non app user id like for root user 0 as well, but this call
     * is expensive due to usage of reflection, and requires hidden API bypass, check
     * {@link ReflectionUtils#bypassHiddenAPIReflectionRestrictions()} for details.
     * <p>
     * `BlockGuardOs` implements the `Os` interface and its instance is stored in `Libcore` class static `os` field.
     * The `getpwuid` method is implemented by `ForwardingOs`, which is the super class of `BlockGuardOs`.
     * The `getpwuid` method returns `StructPasswd` object whose `pw_name` contains the user name for id.
     * <a href=" <p>
     * https://stackoverflow.com/a/28057">...</a>167/1468<a href="6958
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:libcore/luni/src/main/java/libcore/io/Libco</a>re.java;<a href="l=39
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:libcore/luni/src/main/java/libcore/io/O</a>s.java;l<a href="=279
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:libcore/luni/src/main/java/libcore/io/Block</a>GuardOs.<a href="java
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:libcore/luni/src/main/java/libcore/io/ForwardingO</a>s.java;l<a href="=340
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:libcore/luni/src/main/java/android/system/Struc</a>tPasswd.<a href="java
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:bionic/libc/bionic/grp_p</a>wd.cpp;l<a href="=553
     * ">* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:system/core/libcutils/include/private/android_filesystem_c</a>onfig.h;l=43
     *
     * @param uid The user id.
     * @return Returns the user name if found, otherwise {@code null}.
     */

    private static String getNameForUidFromLibcore(int uid) {
        if (uid < 0)
            return null;
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions();
        try {
            String libcoreClassName = "libcore.io.Libcore";
            Class<?> clazz = Class.forName(libcoreClassName);
            // libcore.io.BlockGuardOs
            Object os;
            try {
                os = ReflectionUtils.invokeField(Class.forName(libcoreClassName), "os", null).value;
            } catch (Exception e) {
                // ClassCastException may be thrown
                return null;
            }
            if (os == null) {
                return null;
            }
            // libcore.io.ForwardingOs
            clazz = os.getClass().getSuperclass();
            if (clazz == null) {
                return null;
            }
            // android.system.StructPasswd
            Object structPasswd;
            try {
                Method getpwuidMethod = ReflectionUtils.getDeclaredMethod(clazz, "getpwuid", int.class);
                if (getpwuidMethod == null)
                    return null;
                structPasswd = ReflectionUtils.invokeMethod(getpwuidMethod, os, uid).value;
            } catch (Exception e) {
                return null;
            }
            if (structPasswd == null) {
                return null;
            }
            try {
                clazz = structPasswd.getClass();
                return (String) ReflectionUtils.invokeField(clazz, "pw_name", structPasswd).value;
            } catch (Exception e) {
                // ClassCastException may be thrown
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
