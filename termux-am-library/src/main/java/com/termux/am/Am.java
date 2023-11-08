/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package com.termux.am;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;

public class Am extends BaseCommand {

    private int mRepeat = 0;

    private String mReceiverPermission;

    private final Application app;

    public Am(PrintStream out, PrintStream err, Application app) {
        super(out, err);
        if (app == null)
            throw new InvalidParameterException("app context can't be null");
        this.app = app;
    }

    @Override
    public void onShowUsage(PrintStream out) {
        PrintWriter pw = new PrintWriter(out);
        pw.println("""
                usage: am [subcommand] [options]
                usage: am start [-D] [-N] [-W] [-P <FILE>] [--start-profiler <FILE>]
                               [--sampling INTERVAL] [-R COUNT] [-S]
                               [--track-allocation] [--user <USER_ID> | current] <INTENT>
                       am startservice [--user <USER_ID> | current] <INTENT>
                       am stopservice [--user <USER_ID> | current] <INTENT>
                       am broadcast [--user <USER_ID> | all | current] <INTENT>
                       am to-uri [INTENT]
                       am to-intent-uri [INTENT]
                       am to-app-uri [INTENT]

                am start: start an Activity.  Options are:
                    -D: enable debugging
                    -N: enable native debugging
                    -W: wait for launch to complete
                    --start-profiler <FILE>: start profiler and send results to <FILE>
                    --sampling INTERVAL: use sample profiling with INTERVAL microseconds
                        between samples (use with --start-profiler)
                    -P <FILE>: like above, but profiling stops when app goes idle
                    -R: repeat the activity launch <COUNT> times.  Prior to each repeat,
                        the top activity will be finished.
                    -S: force stop the target app before starting the activity
                    --track-allocation: enable tracking of object allocations
                    --user <USER_ID> | current: Specify which user to run as; if not
                        specified then run as the current user.
                    --stack <STACK_ID>: Specify into which stack should the activity be put.
                am startservice: start a Service.  Options are:
                    --user <USER_ID> | current: Specify which user to run as; if not
                        specified then run as the current user.

                am stopservice: stop a Service.  Options are:
                    --user <USER_ID> | current: Specify which user to run as; if not
                        specified then run as the current user.

                am broadcast: send a broadcast Intent.  Options are:
                    --user <USER_ID> | all | current: Specify which user to send to; if not
                        specified then send to all users.
                    --receiver-permission <PERMISSION>: Require receiver to hold permission.

                am to-uri: print the given Intent specification as a URI.

                am to-intent-uri: print the given Intent specification as an intent: URI.

                am to-app-uri: print the given Intent specification as an android-app: URI.

                """);
        IntentCmd.printIntentArgsHelp(pw, "");
        pw.flush();
    }

    @Override
    public void onRun() throws Exception {
        String op = nextArgRequired();
        switch(op) {
            case "start":
                runStart();
                break;
            case "startservice":
                runStartService();
                break;
            case "stopservice":
                runStopService();
                break;
            case "broadcast":
                sendBroadcast();
                break;
            case "to-uri":
                runToUri(0);
                break;
            case "to-intent-uri":
                runToUri(Intent.URI_INTENT_SCHEME);
                break;
            case "to-app-uri":
                runToUri(Intent.URI_ANDROID_APP_SCHEME);
                break;
            default:
                showError("Error: unknown command '" + op + "'");
                break;
        }
    }

    private Intent makeIntent() throws URISyntaxException {
        mRepeat = 0;
        return IntentCmd.parseCommandArgs(mArgs, (opt) -> {
            switch(opt) {
                case "-W":
                case "-P":
                case "--stack":
                case "--sampling":
                case "--start-profiler":
                case "-S":
                    break;
                case "-R":
                    mRepeat = Integer.parseInt(nextArgRequired());
                    break;
                case "--user":
                    nextArgRequired();
                    break;
                case "--receiver-permission":
                    mReceiverPermission = nextArgRequired();
                    break;
                default:
                    return false;
            }
            return true;
        });
    }

    private void runStartService() throws Exception {
        Intent intent = makeIntent();
        out.println("Starting service: " + intent);
        ServiceInfo info;
        try {
            info = app.getPackageManager().getServiceInfo(intent.getComponent(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            err.println("Error: Not found; no service started.");
            return;
        }
        try {
            app.startService(intent);
        } catch (SecurityException e) {
            if (info != null && info.permission != null && !info.permission.isEmpty()) {
                err.println("Error: Requires permission " + info.permission);
            } else {
                err.println("Could not start service");
            }
        } catch (IllegalArgumentException e) {
            err.println("Could not start service");
        }
    }

    private void runStopService() throws Exception {
        Intent intent = makeIntent();
        out.println("Stopping service: " + intent);
        ServiceInfo info;
        try {
            info = app.getPackageManager().getServiceInfo(intent.getComponent(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            err.println("Error: Not found; Service not stopped.");
            return;
        }
        try {
            app.stopService(intent);
        } catch (SecurityException e) {
            if (info != null && info.permission != null && !info.permission.isEmpty()) {
                err.println("Error: Requires permission " + info.permission);
            } else {
                err.println("Error stopping service");
            }
        } catch (IllegalArgumentException e) {
            err.println("Error stopping service");
        }
    }

    private void runStart() throws Exception {
        Intent intent = makeIntent();
        do {
            out.println("Starting: " + intent);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                app.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                out.println("Error: Activity class " + intent.getComponent().toShortString() + " does not exist.");
            } catch (Exception e) {
                err.println("Exception while starting Activity:");
                e.printStackTrace(err);
            }
            mRepeat--;
        } while (mRepeat > 1);
    }

    private void sendBroadcast() throws Exception {
        Intent intent = makeIntent();
        IntentReceiver receiver = new IntentReceiver();
        out.println("Broadcasting: " + intent);
        app.sendOrderedBroadcast(intent, mReceiverPermission, receiver, new Handler(app.getMainLooper()), Activity.RESULT_OK, null, null);
        receiver.waitForFinish();
    }

    private void runToUri(int flags) throws Exception {
        Intent intent = makeIntent();
        out.println(intent.toUri(flags));
    }

    private class IntentReceiver extends BroadcastReceiver {

        private boolean mFinished = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            String line = "Broadcast completed: result=" + getResultCode();
            if (getResultData() != null)
                line = line + ", data=\"" + getResultData() + "\"";
            if (getResultExtras(false) != null)
                line = line + ", extras: " + getResultExtras(false);
            out.println(line);
            synchronized (this) {
                mFinished = true;
                notifyAll();
            }
        }

        public synchronized void waitForFinish() {
            try {
                while (!mFinished) wait();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
