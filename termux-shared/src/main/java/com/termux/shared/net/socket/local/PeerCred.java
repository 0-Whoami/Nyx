package com.termux.shared.net.socket.local;

import android.content.Context;

import com.termux.shared.android.ProcessUtils;
import com.termux.shared.android.UserUtils;

/**
 * The {@link PeerCred} of the {@link LocalClientSocket} containing info of client/peer.
 */

public class PeerCred {


    /**
     * Process Id.
     */
    public final int pid;
    /**
     * User Id.
     */
    public final int uid;
    /**
     * Group Id.
     */
    public final int gid;
    /**
     * Process Name.
     */
    public String pname;
    /**
     * User name.
     */
    public String uname;


    PeerCred() {
        // Initialize to -1 instead of 0 in case a failed getPeerCred()/getsockopt() call somehow doesn't report failure and returns the uid of root
        pid = -1;
        uid = -1;
        gid = -1;
    }

    /**
     * Set data that was not set by JNI.
     */
    public void fillPeerCred(Context context) {
        fillUnameAndGname(context);
        fillPname(context);
    }

    /**
     * Set {@link #uname} and  if not set.
     */
    public void fillUnameAndGname(Context context) {
        uname = UserUtils.getNameForUid(context, uid);
    }

    /**
     * Set {@link #pname} if not set.
     */
    public void fillPname(Context context) {
        // If jni did not set process name since it wouldn't be able to access /proc/<pid> of other
        // users/apps, then try to see if any app has that pid, but this wouldn't check child
        // processes of the app.
        if (pid > 0 && pname == null)
            pname = ProcessUtils.getAppProcessNameForPid(context, pid);
    }

}
