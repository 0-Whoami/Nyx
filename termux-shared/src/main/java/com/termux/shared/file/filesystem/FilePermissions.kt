/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
 */
package com.termux.shared.file.filesystem

/**
 * This class consists exclusively of static methods that operate on sets of
 * [FilePermission] objects.
 *
 * [* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/nio/file/attribute/PosixFilePermissions.](
  )java
 *
 * @since 1.7
 */
object FilePermissions {
    // Write string representation of permission bits to {@code sb}.
    private fun writeBits(sb: StringBuilder, r: Boolean, w: Boolean, x: Boolean) {
        if (r) {
            sb.append('r')
        } else {
            sb.append('-')
        }
        if (w) {
            sb.append('w')
        } else {
            sb.append('-')
        }
        if (x) {
            sb.append('x')
        } else {
            sb.append('-')
        }
    }

    /**
     * Returns the `String` representation of a set of permissions. It
     * is guaranteed that the returned `String` can be parsed by the
     * method.
     *
     *
     *  If the set contains `null` or elements that are not of type
     * `FilePermission` then these elements are ignored.
     *
     * @param perms the set of permissions
     * @return the string representation of the permission set
     */
    fun toString(perms: Collection<FilePermission?>): String {
        val sb = StringBuilder(9)
        writeBits(
            sb,
            perms.contains(FilePermission.OWNER_READ),
            perms.contains(FilePermission.OWNER_WRITE),
            perms.contains(
                FilePermission.OWNER_EXECUTE
            )
        )
        writeBits(
            sb,
            perms.contains(FilePermission.GROUP_READ),
            perms.contains(FilePermission.GROUP_WRITE),
            perms.contains(
                FilePermission.GROUP_EXECUTE
            )
        )
        writeBits(
            sb,
            perms.contains(FilePermission.OTHERS_READ),
            perms.contains(FilePermission.OTHERS_WRITE),
            perms.contains(
                FilePermission.OTHERS_EXECUTE
            )
        )
        return sb.toString()
    }
}
