/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.termux.shared.file.filesystem;

import android.system.StructStat;

import java.io.File;
import java.io.IOException;

/**
 * Unix implementation of PosixFileAttributes.
 * <a href="https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/sun/nio/fs/UnixFileAttributes.java">...</a>
 */
public class FileAttributes {

    private int st_mode;

    private FileAttributes(String path) {
    }

    // get the FileAttributes for a given file
    public static FileAttributes get(String filePath, boolean followLinks) throws IOException {
        FileAttributes fileAttributes;
        if (filePath == null || filePath.isEmpty())
            fileAttributes = new FileAttributes(null);
        else
            fileAttributes = new FileAttributes(new File(filePath).getAbsolutePath());
        if (followLinks) {
            NativeDispatcher.stat(filePath, fileAttributes);
        } else {
            NativeDispatcher.lstat(filePath, fileAttributes);
        }
        // Logger.logDebug(fileAttributes.toString());
        return fileAttributes;
    }

    public final boolean isRegularFile() {
        return ((st_mode & UnixConstants.S_IFMT) == UnixConstants.S_IFREG);
    }

    public final boolean isDirectory() {
        return ((st_mode & UnixConstants.S_IFMT) == UnixConstants.S_IFDIR);
    }

    public final boolean isSymbolicLink() {
        return ((st_mode & UnixConstants.S_IFMT) == UnixConstants.S_IFLNK);
    }

    public final boolean isCharacter() {
        return ((st_mode & UnixConstants.S_IFMT) == UnixConstants.S_IFCHR);
    }

    public final boolean isFifo() {
        return ((st_mode & UnixConstants.S_IFMT) == UnixConstants.S_IFIFO);
    }

    public final boolean isSocket() {
        return ((st_mode & UnixConstants.S_IFMT) == UnixConstants.S_IFSOCK);
    }

    public final boolean isBlock() {
        return ((st_mode & UnixConstants.S_IFMT) == UnixConstants.S_IFBLK);
    }

    public final void loadFromStructStat(StructStat structStat) {
        this.st_mode = structStat.st_mode;
    }


}
