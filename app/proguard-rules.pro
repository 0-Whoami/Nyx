# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
-optimizations aggressive
-optimizationpasses 20
-allowaccessmodification
-dontskipnonpubliclibraryclasses
-repackageclasses ''
-mergeinterfacesaggressively
-overloadaggressively
#-dontpreverify
#-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

# Temp fix for androidx.window:window:1.0.0-alpha09 imported by termux-shared
# https://issuetracker.google.com/issues/189001730
# https://android-review.googlesource.com/c/platform/frameworks/support/+/1757630
##-keep class androidx.window.** { *; }
#-keep,allowobfuscation,allowoptimization,allowshrinking class com.termux.shared.** {*;}
#-keep,allowobfuscation,allowoptimization,allowshrinking class com.termux.terminal.** {*;}
#-keep,allowobfuscation,allowoptimization,allowshrinking class com.termux.view.** {*;}
#
#-dontwarn com.termux.shared.file.FileUtils
#-dontwarn com.termux.shared.shell.command.ExecutionCommand$Runner$TERMINAL_SESSION
#-dontwarn com.termux.shared.shell.command.ExecutionCommand
#-dontwarn com.termux.shared.shell.command.environment.UnixShellEnvironment
#-dontwarn com.termux.shared.termux.TermuxConstants
#-dontwarn com.termux.shared.termux.file.TermuxFileUtils
#-dontwarn com.termux.shared.termux.shell.TermuxShellManager$Companion
#-dontwarn com.termux.shared.termux.shell.TermuxShellManager
#-dontwarn com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
#-dontwarn com.termux.shared.termux.shell.command.runner.terminal.TermuxSession$Companion
#-dontwarn com.termux.shared.termux.shell.command.runner.terminal.TermuxSession$TermuxSessionClient
#-dontwarn com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
#-dontwarn com.termux.shared.view.BackgroundBlur
#-dontwarn com.termux.terminal.TerminalSession
#-dontwarn com.termux.terminal.TerminalSessionClient
#-dontwarn com.termux.view.TerminalView
#-dontwarn com.termux.view.TerminalViewClient
