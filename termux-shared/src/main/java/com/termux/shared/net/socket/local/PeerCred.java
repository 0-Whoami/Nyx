package com.termux.shared.net.socket.local;

import android.content.Context;

import com.termux.shared.android.ProcessUtils;

/**
 * The {@link PeerCred} of the {@link LocalClientSocket} containing info of client/peer.
 */

public class PeerCred {


    /**
     * User Id.
     */
    public final int uid;
    /**
     * Process Id.
     */
    private final int pid;
    /**
     * Process Name.
     */
    private String pname;

    PeerCred() {
        // Initialize to -1 instead of 0 in case a failed getPeerCred()/getsockopt() call somehow doesn't report failure and returns the uid of root
        pid = -1;
        uid = -1;
    }

    /**
     * Set data that was not set by JNI.
     */
    public final void fillPeerCred(Context context) {

        fillPname(context);
    }


    /**
     * Set {@link #pname} if not set.
     */
    private final void fillPname(Context context) {
        // If jni did not set process name since it wouldn't be able to access /proc/<pid> of other
        // users/apps, then try to see if any app has that pid, but this wouldn't check child
        // processes of the app.
        if (pid > 0 && pname == null)
            pname = ProcessUtils.getAppProcessNameForPid(context, pid);
    }

}
