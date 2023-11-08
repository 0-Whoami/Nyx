package com.termux.shared.termux;

import androidx.annotation.Nullable;

public class TermuxBootstrap {


    /**
     * The {@link PackageManager} for the bootstrap in the app APK added in app/build.gradle.
     */
    public static PackageManager TERMUX_APP_PACKAGE_MANAGER;

    /**
     * The {@link PackageVariant} for the bootstrap in the app APK added in app/build.gradle.
     */
    public static PackageVariant TERMUX_APP_PACKAGE_VARIANT;

    /**
     * Set {@link #TERMUX_APP_PACKAGE_VARIANT} and {@link #TERMUX_APP_PACKAGE_MANAGER} from {@code packageVariantName} passed.
     */
    public static void setTermuxPackageManagerAndVariant(@Nullable String packageVariantName) {
        TERMUX_APP_PACKAGE_VARIANT = PackageVariant.variantOf(packageVariantName);
        if (TERMUX_APP_PACKAGE_VARIANT == null) {
            throw new RuntimeException("Unsupported TERMUX_APP_PACKAGE_VARIANT \"" + packageVariantName + "\"");
        }
        // Set packageManagerName to substring before first dash "-" in packageVariantName
        int index = packageVariantName.indexOf('-');
        String packageManagerName = (index == -1) ? null : packageVariantName.substring(0, index);
        TERMUX_APP_PACKAGE_MANAGER = PackageManager.managerOf(packageManagerName);
        if (TERMUX_APP_PACKAGE_MANAGER == null) {
            throw new RuntimeException("Unsupported TERMUX_APP_PACKAGE_MANAGER \"" + packageManagerName + "\" with variant \"" + packageVariantName + "\"");
        }
    }

    /**
     * Is {@link PackageVariant#APT_ANDROID_5} set as {@link #TERMUX_APP_PACKAGE_VARIANT}.
     */
    public static boolean isAppPackageVariantAPTAndroid5() {
        return PackageVariant.APT_ANDROID_5.equals(TERMUX_APP_PACKAGE_VARIANT);
    }

    /**
     * Termux package manager.
     */
    public enum PackageManager {

        /**
         * Advanced Package Tool (APT) for managing debian deb package files.
         * <a href="https://wiki.debian.org/Apt">...</a>
         * <a href="https://wiki.debian.org/deb">...</a>
         */
        APT();

        ///**
        // * Termux Android Package Manager (TAPM) for managing termux apk package files.
        // * https://en.wikipedia.org/wiki/Apk_(file_format)
        // */
        //TAPM("tapm");
        ///**
        // * Package Manager (PACMAN) for managing arch linux pkg.tar package files.
        // * https://wiki.archlinux.org/title/pacman
        // * https://en.wikipedia.org/wiki/Arch_Linux#Pacman
        // */
        //PACMAN("pacman");
        private final String name;

        PackageManager() {
            this.name = "apt";
        }

        public String getName() {
            return name;
        }

        /**
         * Get {@link PackageManager} for {@code name} if found, otherwise {@code null}.
         */
        @Nullable
        public static PackageManager managerOf(String name) {
            if (name == null || name.isEmpty())
                return null;
            for (PackageManager v : PackageManager.values()) {
                if (v.name.equals(name)) {
                    return v;
                }
            }
            return null;
        }
    }

    /**
     * Termux package variant. The substring before first dash "-" must match one of the {@link PackageManager}.
     */
    public enum PackageVariant {

        /**
         * {@link PackageManager#APT} variant for Android 7+.
         */
        APT_ANDROID_7("apt-android-7"),
        /**
         * {@link PackageManager#APT} variant for Android 5+.
         */
        APT_ANDROID_5("apt-android-5");

        ///** {@link PackageManager#TAPM} variant for Android 7+. */
        //TAPM_ANDROID_7("tapm-android-7");
        ///** {@link PackageManager#PACMAN} variant for Android 7+. */
        //PACMAN_ANDROID_7("pacman-android-7");
        private final String name;

        PackageVariant(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        /**
         * Get {@link PackageVariant} for {@code name} if found, otherwise {@code null}.
         */
        @Nullable
        public static PackageVariant variantOf(String name) {
            if (name == null || name.isEmpty())
                return null;
            for (PackageVariant v : PackageVariant.values()) {
                if (v.name.equals(name)) {
                    return v;
                }
            }
            return null;
        }
    }
}
