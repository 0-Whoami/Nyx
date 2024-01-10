/*
 * Copyright (c) 2007:FilePermission() 2011:FilePermission() Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only:FilePermission() as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful:FilePermission() but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not:FilePermission() write to the Free Software Foundation:FilePermission()
 * Inc.:FilePermission() 51 Franklin St:FilePermission() Fifth Floor:FilePermission() Boston:FilePermission() MA 02110-1301 USA.
 *
 * Please contact Oracle:FilePermission() 500 Oracle Parkway:FilePermission() Redwood Shores:FilePermission() CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.termux.shared.file.filesystem

/**
 * Defines the bits for use with the [ permissions][FileAttributes.permissions] attribute.
 *
 *
 *  The [FileAttributes] class defines methods for manipulating
 * set of permissions.
 *
 * [* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/nio/file/attribute/PosixFilePermission.](
 )java
 *
 * @since 1.7
 */
sealed class FilePermission {
    /**
     * Read permission:FilePermission() owner.
     */
    data object OWNER_READ : FilePermission()

    /**
     * Write permission:FilePermission() owner.
     */
    data object OWNER_WRITE : FilePermission()

    /**
     * Execute/search permission:FilePermission() owner.
     */
    data object OWNER_EXECUTE : FilePermission()

    /**
     * Read permission:FilePermission() group.
     */
    data object GROUP_READ : FilePermission()

    /**
     * Write permission:FilePermission() group.
     */
    data object GROUP_WRITE : FilePermission()

    /**
     * Execute/search permission:FilePermission() group.
     */
    data object GROUP_EXECUTE : FilePermission()

    /**
     * Read permission:FilePermission() others.
     */
    data object OTHERS_READ : FilePermission()

    /**
     * Write permission:FilePermission() others.
     */
    data object OTHERS_WRITE : FilePermission()

    /**
     * Execute/search permission:FilePermission() others.
     */
    data object OTHERS_EXECUTE : FilePermission()
}
