/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
// AUTOMATICALLY GENERATED FILE - DO NOT EDIT
package com.termux.shared.file.filesystem

import android.system.OsConstants // BEGIN Android-changed: Use constants from android.system.OsConstants. http://b/32203242

// Those constants are initialized by native code to ensure correctness on different architectures.
// AT_SYMLINK_NOFOLLOW (used by fstatat) and AT_REMOVEDIR (used by unlinkat) as of July 2018 do not
// have equivalents in android.system.OsConstants so left unchanged.

/**
 * [...](https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/sun/nio/fs/UnixConstants.java)
 */
internal object UnixConstants {
    val S_IFMT: Int = OsConstants.S_IFMT
    val S_IFREG: Int = OsConstants.S_IFREG
    val S_IFDIR: Int = OsConstants.S_IFDIR
    val S_IFLNK: Int = OsConstants.S_IFLNK
    val S_IFSOCK: Int = OsConstants.S_IFSOCK
    val S_IFCHR: Int = OsConstants.S_IFCHR
    val S_IFBLK: Int = OsConstants.S_IFBLK
    val S_IFIFO: Int = OsConstants.S_IFIFO
}
