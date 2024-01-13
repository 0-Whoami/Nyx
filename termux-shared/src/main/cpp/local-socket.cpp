#include <cstdio>
#include <ctime>
#include <cerrno>
#include <jni.h>
#include <sstream>
#include <string>
#include <unistd.h>

#include <android/log.h>

#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>

using namespace std;


/* Convert milliseconds to timeval. */
timeval milliseconds_to_timeval(int milliseconds) {
    struct timeval tv = {};
    tv.tv_sec = milliseconds / 1000;
    tv.tv_usec = (milliseconds % 1000) * 1000;
    return tv;
}


// Note: Exceptions thrown from JNI must be caught with Throwable class instead of Exception,
// otherwise exception will be sent to UncaughtExceptionHandler of the thread.
// Android studio complains that getJniResult functions always return nullptr since linter is broken
// for jboolean and jobject if comparisons.
bool checkJniException(JNIEnv *env) {
    if (env->ExceptionCheck()) {
        jthrowable throwable = env->ExceptionOccurred();
        if (throwable != NULL) {
            env->ExceptionClear();
            env->Throw(throwable);
            return true;
        }
    }

    return false;
}

/* Get "com/termux/shared/jni/models/JniResult" object that can be returned as result for a JNI call. */
jobject getJniResult(JNIEnv *env, const int retvalParam, const int intDataParam) {
    jclass clazz = env->FindClass("com/termux/shared/jni/models/JniResult");
    if (checkJniException(env)) return NULL;
    if (!clazz) {
        return NULL;
    }

    jmethodID constructor = env->GetMethodID(clazz, "<init>", "(II)V");
    if (checkJniException(env)) return NULL;
    if (!constructor) {
        return NULL;
    }

    jobject obj = env->NewObject(clazz, constructor, retvalParam, intDataParam);
    if (checkJniException(env)) return NULL;
    if (obj == NULL) {
        return NULL;
    }

    return obj;
}


jobject getJniResult(JNIEnv *env, const int retvalParam) {
    return getJniResult(env, retvalParam, 0);
}

jobject getJniResult(JNIEnv *env) {
    return getJniResult(env, 0, 0);
}


extern "C"
JNIEXPORT jobject

JNICALL
Java_com_termux_shared_net_socket_local_LocalSocketManager_createServerSocketNative(JNIEnv *env,
                                                                                    jclass clazz,
                                                                                    jbyteArray pathArray,
                                                                                    jint backlog) {
    if (backlog < 1 || backlog > 500) {
        return getJniResult(env, -1);
    }

    // Create server socket
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd == -1) {
        return getJniResult(env, -1, errno);
    }

    jbyte *path = env->GetByteArrayElements(pathArray, nullptr);
    if (checkJniException(env)) return NULL;
    if (path == nullptr) {
        close(fd);
        return getJniResult(env, -1);
    }

    // On Linux, sun_path is 108 bytes (UNIX_PATH_MAX) in size
    int chars = env->GetArrayLength(pathArray);
    if (checkJniException(env)) return NULL;
    if (chars >= 108) {
        env->ReleaseByteArrayElements(pathArray, path, JNI_ABORT);
        if (checkJniException(env)) return NULL;
        close(fd);
        return getJniResult(env, -1);
    }

    struct sockaddr_un adr = {.sun_family = AF_UNIX};
    memcpy(&adr.sun_path, path, chars);

    // Bind path to server socket
    if (::bind(fd, reinterpret_cast<struct sockaddr *>(&adr), sizeof(adr)) == -1) {
        int errnoBackup = errno;
        env->ReleaseByteArrayElements(pathArray, path, JNI_ABORT);
        if (checkJniException(env)) return NULL;
        close(fd);
        return getJniResult(env, -1, errnoBackup);
    }

    // Start listening for client sockets on server socket
    if (listen(fd, backlog) == -1) {
        int errnoBackup = errno;
        env->ReleaseByteArrayElements(pathArray, path, JNI_ABORT);
        if (checkJniException(env)) return NULL;
        close(fd);
        return getJniResult(env, -1, errnoBackup);
    }

    env->ReleaseByteArrayElements(pathArray, path, JNI_ABORT);
    if (checkJniException(env)) return NULL;

    // Return success and server socket fd in JniResult.intData field
    return getJniResult(env, 0, fd);
}

extern "C"
JNIEXPORT jobject

JNICALL
Java_com_termux_shared_net_socket_local_LocalSocketManager_closeSocketNative(JNIEnv *env,
                                                                             jclass clazz,
                                                                             jint fd) {
    if (fd < 0) {
        return getJniResult(env, -1);
    }

    if (close(fd) == -1) {
        return getJniResult(env, -1, errno);
    }

    // Return success
    return getJniResult(env);
}

extern "C"
JNIEXPORT jobject

JNICALL
Java_com_termux_shared_net_socket_local_LocalSocketManager_acceptNative(JNIEnv *env, jclass clazz,
                                                                        jint fd) {
    if (fd < 0) {
        return getJniResult(env, -1);
    }

    // Accept client socket
    int clientFd = accept(fd, nullptr, nullptr);
    if (clientFd == -1) {
        return getJniResult(env, -1, errno);
    }

    // Return success and client socket fd in JniResult.intData field
    return getJniResult(env, 0, clientFd);
}


/* Sets socket option timeout in milliseconds. */
int set_socket_timeout(int fd, int option, int timeout) {
    struct timeval tv = milliseconds_to_timeval(timeout);
    socklen_t len = sizeof(tv);
    return setsockopt(fd, SOL_SOCKET, option, &tv, len);
}

extern "C"
JNIEXPORT jobject

JNICALL
Java_com_termux_shared_net_socket_local_LocalSocketManager_setSocketReadTimeoutNative(JNIEnv *env,
                                                                                      jclass clazz,
                                                                                      jint fd,
                                                                                      jint timeout) {
    if (fd < 0) {
        return getJniResult(env, 0, -1);
    }

    if (set_socket_timeout(fd, SO_RCVTIMEO, timeout) == -1) {
        return getJniResult(env, -1, errno);
    }

    // Return success
    return getJniResult(env);
}

extern "C"
JNIEXPORT jobject

JNICALL
Java_com_termux_shared_net_socket_local_LocalSocketManager_setSocketSendTimeoutNative(JNIEnv *env,
                                                                                      jclass clazz,
                                                                                      jint fd,
                                                                                      jint timeout) {
    if (fd < 0) {
        return getJniResult(env, 0, -1);
    }

    if (set_socket_timeout(fd, SO_SNDTIMEO, timeout) == -1) {
        return getJniResult(env, -1, errno);
    }

    // Return success
    return getJniResult(env);
}

extern "C"
JNIEXPORT jobject

JNICALL
Java_com_termux_shared_net_socket_local_LocalSocketManager_getPeerCredNative(JNIEnv *env,
                                                                             jclass clazz,
                                                                             jint fd) {
    if (fd < 0) {
        return getJniResult(env, 0, -1);
    }

    // Initialize to -1 instead of 0 in case a failed getsockopt() call somehow doesn't report failure and returns the uid of root
    struct ucred cred = {};
    cred.pid = -1;
    cred.uid = -1;
    cred.gid = -1;

    socklen_t len = sizeof(cred);

    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cred, &len) == -1) {
        return getJniResult(env, -1, errno);
    }

    // Fill "com.termux.shared.net.socket.local.PeerCred" object.
    // The pid, uid and gid will always be set based on ucred.
    // The pname and cmdline will only be set if current process has access to "/proc/[pid]/cmdline"
    // of peer process. Processes of other users/apps are not normally accessible.
    if (checkJniException(env)) return NULL;
    return getJniResult(env);
}
