#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define NYX_UNUSED(x) x __attribute__((__unused__))
#ifdef __APPLE__
# define LACKS_PTSNAME_R
#endif

static int
create_subprocess(char const *cmd, int *pProcessId, jint rows, jint columns, jint cell_width,
                  jint cell_height) {
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);

#ifdef LACKS_PTSNAME_R
    char* devname;
#else
    char devname[64];
#endif
    if (grantpt(ptm) || unlockpt(ptm) ||
        #ifdef LACKS_PTSNAME_R
        (devname = ptsname(ptm)) == NULL
        #else
        ptsname_r(ptm, devname, sizeof(devname))
#endif
            ) {}

    // Enable UTF-8 mode and disable flow control to prevent Ctrl+S from locking up the display.
    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(ptm, TCSANOW, &tios);

    /** Set initial winsize. */
    struct winsize sz = {.ws_row = (unsigned short) rows, .ws_col = (unsigned short) columns, .ws_xpixel = (unsigned short) (
            columns * cell_width), .ws_ypixel = (unsigned short) (rows * cell_height)};
    ioctl(ptm, TIOCSWINSZ, &sz);

    pid_t pid = fork();
    if (pid > 0) {
        *pProcessId = (int) pid;
        return ptm;
    } else {
        // Clear signals which the Android java process may have blocked:
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

        close(ptm);
        setsid();

        int pts = open(devname, O_RDWR);
        if (pts < 0) exit(-1);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);

        DIR *self_dir = opendir("/proc/self/fd");
        if (self_dir != NULL) {
            int self_dir_fd = dirfd(self_dir);
            struct dirent *entry;
            while ((entry = readdir(self_dir)) != NULL) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != self_dir_fd) close(fd);
            }
            closedir(self_dir);
        }

        clearenv();
//        if (envp) for (; *envp; ++envp) putenv(*envp);

        if (chdir("/data/data/com.termux/files/home/") != 0) {
            char *error_message;
            // No need to free asprintf()-allocated memory since doing execvp() or exit() below.
            if (asprintf(&error_message, "chdir(\"%s\")", "/data/data/com.termux/files/home/") ==
                -1)
                error_message = "chdir()";
            perror(error_message);
            fflush(stderr);
        }
        execvp(cmd, NULL);
        // Show terminal output about failing exec() call:
        char *error_message;
        if (asprintf(&error_message, "exec(\"%s\")", cmd) == -1) error_message = "exec()";
        perror(error_message);
        _exit(1);
    }
}

JNIEXPORT jint

JNICALL Java_com_termux_terminal_JNI_process(
        JNIEnv *env,
        jclass NYX_UNUSED(clazz),
        jboolean failsafe,
        jintArray processIdArray,
        jint rows,
        jint columns,
        jint cell_width,
        jint cell_height) {

    int procId = 0;
    char const *cmd_utf8 =
            failsafe == JNI_TRUE ? "/system/bin/sh" : "/data/data/com.termux/files/usr/bin/login";
    int ptm = create_subprocess(cmd_utf8, &procId, rows, columns,
                                cell_width, cell_height);

    int *pProcId = (int *) (*env)->GetPrimitiveArrayCritical(env, processIdArray, NULL);
    *pProcId = procId;
    (*env)->ReleasePrimitiveArrayCritical(env, processIdArray, pProcId, 0);
    return ptm;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_size(JNIEnv
                                  *NYX_UNUSED(env),
                                  jclass NYX_UNUSED(clazz), jint
                                  fd,
                                  jint rows, jint
                                  cols,
                                  jint cell_width, jint
                                  cell_height) {
    struct winsize sz = {.ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols, .ws_xpixel = (unsigned short) (
            cols * cell_width), .ws_ypixel = (unsigned short) (rows * cell_height)};
    ioctl(fd, TIOCSWINSZ,
          &sz);
}

JNIEXPORT jint

JNICALL
Java_com_termux_terminal_JNI_waitFor(JNIEnv *NYX_UNUSED(env), jclass NYX_UNUSED(clazz),
                                     jint pid) {
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    }
    // Should never happen - waitpid(2) says "One of the first three macros will evaluate to a non-zero (true) value".
    return 0;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_close(JNIEnv
                                   *NYX_UNUSED(env),
                                   jclass NYX_UNUSED(clazz), jint
                                   fileDescriptor) {
    close(fileDescriptor);
}
