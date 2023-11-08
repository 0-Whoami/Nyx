package com.termux.shared.termux

object TermuxBootstrap {
    /**
     * The [PackageManager] for the bootstrap in the app APK added in app/build.gradle.
     */
    @JvmField
    var TERMUX_APP_PACKAGE_MANAGER: PackageManager? = null

    /**
     * The [PackageVariant] for the bootstrap in the app APK added in app/build.gradle.
     */
    @JvmField
    var TERMUX_APP_PACKAGE_VARIANT: PackageVariant? = PackageVariant.APT_ANDROID_7

    /**
     * Set [.TERMUX_APP_PACKAGE_VARIANT] and [.TERMUX_APP_PACKAGE_MANAGER] from `packageVariantName` passed.
     */
//    @JvmStatic
//    fun setTermuxPackageManagerAndVariant(packageVariantName: String?) {
//        TERMUX_APP_PACKAGE_VARIANT = PackageVariant.variantOf(packageVariantName)
//        if (TERMUX_APP_PACKAGE_VARIANT == null) {
//            throw RuntimeException("Unsupported TERMUX_APP_PACKAGE_VARIANT \"$packageVariantName\"")
//        }
//        // Set packageManagerName to substring before first dash "-" in packageVariantName
//        val index = packageVariantName!!.indexOf('-')
//        val packageManagerName = if (index == -1) null else packageVariantName.substring(0, index)
//        TERMUX_APP_PACKAGE_MANAGER = PackageManager.managerOf(packageManagerName)
//        if (TERMUX_APP_PACKAGE_MANAGER == null) {
//            throw RuntimeException("Unsupported TERMUX_APP_PACKAGE_MANAGER \"$packageManagerName\" with variant \"$packageVariantName\"")
//        }
//    }



    /**
     * Termux package manager.
     */
    sealed class PackageManager {
        /**
         * Advanced Package Tool (APT) for managing debian deb package files.
         * [...](https://wiki.debian.org/Apt)
         * [...](https://wiki.debian.org/deb)
         */
        data object APT:PackageManager()

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
        val name = "apt"

//        companion object {
//            /**
//             * Get [PackageManager] for `name` if found, otherwise `null`.
//             */
//            fun managerOf(name: String?): PackageManager? {
//                if (name.isNullOrEmpty()) return null
//                return when(name){
//                    "apt" -> APT
//                    else-> null
//                }
//            }
//        }
    }

    /**
     * Termux package variant. The substring before first dash "-" must match one of the [PackageManager].
     */
    sealed class PackageVariant(
        val name: String
    ) {
        /**
         * [PackageManager.APT] variant for Android 7+.
         */
        data object APT_ANDROID_7:PackageVariant("apt-android-7")

//        companion object {
//            /**
//             * Get [PackageVariant] for `name` if found, otherwise `null`.
//             */
//            fun variantOf(name: String?): PackageVariant? {
//                if (name.isNullOrEmpty()) return null
//                return when(name){
//                    "apt-android-7" -> APT_ANDROID_7
//                    else-> null
//                }
//            }
//        }
    }
}
