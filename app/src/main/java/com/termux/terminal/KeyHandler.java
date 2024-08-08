package com.termux.terminal;

import android.view.KeyEvent;

public final class KeyHandler {

    public static final int KEYMOD_ALT = 0x80000000;
    public static final int KEYMOD_CTRL = 0x40000000;
    public static final int KEYMOD_SHIFT = 0x20000000;
    public static final int KEYMOD_NUM_LOCK = 0x10000000;

    private static int get(final String key) {
        // terminfo: http://pubs.opengroup.org/onlinepubs/7990989799/xcurses/terminfo.html
        // termcap: http://man7.org/linux/man-pages/man5/termcap.5.html
        return switch (key) {
            case "%i " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_DPAD_RIGHT;
            case "#2 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_MOVE_HOME; // Shifted home
            case "#4 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_DPAD_LEFT;
            case "*7 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_MOVE_END; // Shifted end key

            case "k1 " -> KeyEvent.KEYCODE_F1;
            case "k2 " -> KeyEvent.KEYCODE_F2;
            case "k3 " -> KeyEvent.KEYCODE_F3;
            case "k4 " -> KeyEvent.KEYCODE_F4;
            case "k5 " -> KeyEvent.KEYCODE_F5;
            case "k6 " -> KeyEvent.KEYCODE_F6;
            case "k7 " -> KeyEvent.KEYCODE_F7;
            case "k8 " -> KeyEvent.KEYCODE_F8;
            case "k9 " -> KeyEvent.KEYCODE_F9;
            case "k; " -> KeyEvent.KEYCODE_F10;
            case "F1 " -> KeyEvent.KEYCODE_F11;
            case "F2 " -> KeyEvent.KEYCODE_F12;
            case "F3 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F1;
            case "F4 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F2;
            case "F5 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F3;
            case "F6 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F4;
            case "F7 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F5;
            case "F8 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F6;
            case "F9 " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F7;
            case "FA " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F8;
            case "FB " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F9;
            case "FC " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F10;
            case "FD " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F11;
            case "FE " -> KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_F12;

            case "kb " -> KeyEvent.KEYCODE_DEL; // backspace key

            case "kd " -> KeyEvent.KEYCODE_DPAD_DOWN; // terminfo=kcud1, down-arrow key
            case "kh " -> KeyEvent.KEYCODE_MOVE_HOME;
            case "kl " -> KeyEvent.KEYCODE_DPAD_LEFT;
            case "kr " -> KeyEvent.KEYCODE_DPAD_RIGHT;

            // K1=Upper left of keypad:
            // t_K1 <kHome> keypad home key
            // t_K3 <kPageUp> keypad page-up key
            // t_K4 <kEnd> keypad end key
            // t_K5 <kPageDown> keypad page-down key
            case "K1 " -> KeyEvent.KEYCODE_MOVE_HOME;
            case "K3 ", "kN " -> KeyEvent.KEYCODE_PAGE_UP;
            case "K4 ", "@7 " -> KeyEvent.KEYCODE_MOVE_END;
            case "K5 ", "kP " -> KeyEvent.KEYCODE_PAGE_DOWN;

            case "ku " -> KeyEvent.KEYCODE_DPAD_UP;

            case "kB " ->
                    KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_TAB; // termcap=kB, terminfo=kcbt: Back-tab
            case "kD " -> KeyEvent.KEYCODE_FORWARD_DEL; // terminfo=kdch1, delete-character key
            case "kDN " ->
                    KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_DPAD_DOWN; // non-standard shifted arrow down
            case "kF " ->
                    KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_DPAD_DOWN; // terminfo=kind, scroll-forward key
            case "kI " -> KeyEvent.KEYCODE_INSERT;
            case "kR " ->
                    KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_DPAD_UP; // terminfo=kri, scroll-backward key
            case "kUP " ->
                    KeyHandler.KEYMOD_SHIFT | KeyEvent.KEYCODE_DPAD_UP; // non-standard shifted up

            case "@8 " -> KeyEvent.KEYCODE_NUMPAD_ENTER;
            default -> 0;
        };

    }

    static String getCodeFromTermcap(final String termcap, final boolean cursorKeysApplication, final boolean keypadApplication) {
        int keyCode = KeyHandler.get(termcap);
        if (0 == keyCode) return null;
        int keyMod = 0;
        if (0 != (keyCode & KeyHandler.KEYMOD_SHIFT)) {
            keyMod |= KeyHandler.KEYMOD_SHIFT;
            keyCode &= ~KeyHandler.KEYMOD_SHIFT;
        }
        if (0 != (keyCode & KeyHandler.KEYMOD_CTRL)) {
            keyMod |= KeyHandler.KEYMOD_CTRL;
            keyCode &= ~KeyHandler.KEYMOD_CTRL;
        }
        if (0 != (keyCode & KeyHandler.KEYMOD_ALT)) {
            keyMod |= KeyHandler.KEYMOD_ALT;
            keyCode &= ~KeyHandler.KEYMOD_ALT;
        }
        if (0 != (keyCode & KeyHandler.KEYMOD_NUM_LOCK)) {
            keyMod |= KeyHandler.KEYMOD_NUM_LOCK;
            keyCode &= ~KeyHandler.KEYMOD_NUM_LOCK;
        }
        return KeyHandler.getCode(keyCode, keyMod, cursorKeysApplication, keypadApplication);
    }

    public static String getCode(final int keyCode, int keyMode, final boolean cursorApp, final boolean keypadApplication) {
        final boolean numLockOn = 0 != (keyMode & KeyHandler.KEYMOD_NUM_LOCK);
        keyMode &= ~KeyHandler.KEYMOD_NUM_LOCK;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return "\015";

            case KeyEvent.KEYCODE_DPAD_UP:
                return (0 == keyMode) ? (cursorApp ? "\033OA" : "\033[A") : KeyHandler.transformForModifiers("\033[1", keyMode, 'A');
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return (0 == keyMode) ? (cursorApp ? "\033OB" : "\033[B") : KeyHandler.transformForModifiers("\033[1", keyMode, 'B');
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return (0 == keyMode) ? (cursorApp ? "\033OC" : "\033[C") : KeyHandler.transformForModifiers("\033[1", keyMode, 'C');
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return (0 == keyMode) ? (cursorApp ? "\033OD" : "\033[D") : KeyHandler.transformForModifiers("\033[1", keyMode, 'D');

            case KeyEvent.KEYCODE_MOVE_HOME:
                // Note that KEYCODE_HOME is handled by the system and never delivered to applications.
                // On a Logitech k810 keyboard KEYCODE_MOVE_HOME is sent by FN+LeftArrow.
                return (0 == keyMode) ? (cursorApp ? "\033OH" : "\033[H") : KeyHandler.transformForModifiers("\033[1", keyMode, 'H');
            case KeyEvent.KEYCODE_MOVE_END:
                return (0 == keyMode) ? (cursorApp ? "\033OF" : "\033[F") : KeyHandler.transformForModifiers("\033[1", keyMode, 'F');

            // An xterm can send function keys F1 to F4 in two modes: vt100 compatible or
            // not. Because Vim may not know what the xterm is sending, both types of keys
            // are recognized. The same happens for the <Home> and <End> keys.
            // normal vt100 ~
            // <F1> t_k1 <Esc>[11~ <xF1> <Esc>OP *<xF1>-xterm*
            // <F2> t_k2 <Esc>[12~ <xF2> <Esc>OQ *<xF2>-xterm*
            // <F3> t_k3 <Esc>[13~ <xF3> <Esc>OR *<xF3>-xterm*
            // <F4> t_k4 <Esc>[14~ <xF4> <Esc>OS *<xF4>-xterm*
            // <Home> t_kh <Esc>[7~ <xHome> <Esc>OH *<xHome>-xterm*
            // <End> t_@7 <Esc>[4~ <xEnd> <Esc>OF *<xEnd>-xterm*
            case KeyEvent.KEYCODE_F1:
                return (0 == keyMode) ? "\033OP" : KeyHandler.transformForModifiers("\033[1", keyMode, 'P');
            case KeyEvent.KEYCODE_F2:
                return (0 == keyMode) ? "\033OQ" : KeyHandler.transformForModifiers("\033[1", keyMode, 'Q');
            case KeyEvent.KEYCODE_F3:
                return (0 == keyMode) ? "\033OR" : KeyHandler.transformForModifiers("\033[1", keyMode, 'R');
            case KeyEvent.KEYCODE_F4:
                return (0 == keyMode) ? "\033OS" : KeyHandler.transformForModifiers("\033[1", keyMode, 'S');
            case KeyEvent.KEYCODE_F5:
                return KeyHandler.transformForModifiers("\033[15", keyMode, '~');
            case KeyEvent.KEYCODE_F6:
                return KeyHandler.transformForModifiers("\033[17", keyMode, '~');
            case KeyEvent.KEYCODE_F7:
                return KeyHandler.transformForModifiers("\033[18", keyMode, '~');
            case KeyEvent.KEYCODE_F8:
                return KeyHandler.transformForModifiers("\033[19", keyMode, '~');
            case KeyEvent.KEYCODE_F9:
                return KeyHandler.transformForModifiers("\033[20", keyMode, '~');
            case KeyEvent.KEYCODE_F10:
                return KeyHandler.transformForModifiers("\033[21", keyMode, '~');
            case KeyEvent.KEYCODE_F11:
                return KeyHandler.transformForModifiers("\033[23", keyMode, '~');
            case KeyEvent.KEYCODE_F12:
                return KeyHandler.transformForModifiers("\033[24", keyMode, '~');

            case KeyEvent.KEYCODE_SYSRQ:
                return "\033[32~"; // Sys Request / Print
            // Is this Scroll lock? case Cancel: return "\033[33~";
            case KeyEvent.KEYCODE_BREAK:
                return "\033[34~"; // Pause/Break

            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                return "\033";

            case KeyEvent.KEYCODE_INSERT:
                return KeyHandler.transformForModifiers("\033[2", keyMode, '~');
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return KeyHandler.transformForModifiers("\033[3", keyMode, '~');

            case KeyEvent.KEYCODE_PAGE_UP:
                return KeyHandler.transformForModifiers("\033[5", keyMode, '~');
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return KeyHandler.transformForModifiers("\033[6", keyMode, '~');
            case KeyEvent.KEYCODE_DEL:
                final String prefix = (0 == (keyMode & KeyHandler.KEYMOD_ALT)) ? "" : "\033";
                // Just do what xterm and gnome-terminal does:
                return prefix + ((0 == (keyMode & KeyHandler.KEYMOD_CTRL)) ? "\u007F" : "\u0008");
            case KeyEvent.KEYCODE_NUM_LOCK:
                if (keypadApplication) {
                    return "\033OP";
                } else {
                    return null;
                }
            case KeyEvent.KEYCODE_SPACE:
                // If ctrl is not down, return null so that it goes through normal input processing (which may e.g. cause a
                // combining accent to be written):
                return (0 == (keyMode & KeyHandler.KEYMOD_CTRL)) ? null : "\0";
            case KeyEvent.KEYCODE_TAB:
                // This is back-tab when shifted:
                return 0 == (keyMode & KeyHandler.KEYMOD_SHIFT) ? "\011" : "\033[Z";
            case KeyEvent.KEYCODE_ENTER:
                return (0 == (keyMode & KeyHandler.KEYMOD_ALT)) ? "\r" : "\033\r";

            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'M') : "\n";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
                return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'j') : "*";
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'k') : "+";
            case KeyEvent.KEYCODE_NUMPAD_COMMA:
                return ",";
            case KeyEvent.KEYCODE_NUMPAD_DOT:
                if (numLockOn) {
                    return keypadApplication ? "\033On" : ".";
                } else {
                    // DELETE
                    return KeyHandler.transformForModifiers("\033[3", keyMode, '~');
                }
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'm') : "-";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
                return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'o') : "/";
            case KeyEvent.KEYCODE_NUMPAD_0:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'p') : "0";
                } else {
                    // INSERT
                    return KeyHandler.transformForModifiers("\033[2", keyMode, '~');
                }
            case KeyEvent.KEYCODE_NUMPAD_1:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'q') : "1";
                } else {
                    // END
                    return (0 == keyMode) ? (cursorApp ? "\033OF" : "\033[F") : KeyHandler.transformForModifiers("\033[1", keyMode, 'F');
                }
            case KeyEvent.KEYCODE_NUMPAD_2:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'r') : "2";
                } else {
                    // DOWN
                    return (0 == keyMode) ? (cursorApp ? "\033OB" : "\033[B") : KeyHandler.transformForModifiers("\033[1", keyMode, 'B');
                }
            case KeyEvent.KEYCODE_NUMPAD_3:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 's') : "3";
                } else {
                    // PGDN
                    return "\033[6~";
                }
            case KeyEvent.KEYCODE_NUMPAD_4:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 't') : "4";
                } else {
                    // LEFT
                    return (0 == keyMode) ? (cursorApp ? "\033OD" : "\033[D") : KeyHandler.transformForModifiers("\033[1", keyMode, 'D');
                }
            case KeyEvent.KEYCODE_NUMPAD_5:
                return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'u') : "5";
            case KeyEvent.KEYCODE_NUMPAD_6:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'v') : "6";
                } else {
                    // RIGHT
                    return (0 == keyMode) ? (cursorApp ? "\033OC" : "\033[C") : KeyHandler.transformForModifiers("\033[1", keyMode, 'C');
                }
            case KeyEvent.KEYCODE_NUMPAD_7:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'w') : "7";
                } else {
                    // HOME
                    return (0 == keyMode) ? (cursorApp ? "\033OH" : "\033[H") : KeyHandler.transformForModifiers("\033[1", keyMode, 'H');
                }
            case KeyEvent.KEYCODE_NUMPAD_8:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'x') : "8";
                } else {
                    // UP
                    return (0 == keyMode) ? (cursorApp ? "\033OA" : "\033[A") : KeyHandler.transformForModifiers("\033[1", keyMode, 'A');
                }
            case KeyEvent.KEYCODE_NUMPAD_9:
                if (numLockOn) {
                    return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'y') : "9";
                } else {
                    // PGUP
                    return "\033[5~";
                }
            case KeyEvent.KEYCODE_NUMPAD_EQUALS:
                return keypadApplication ? KeyHandler.transformForModifiers("\033O", keyMode, 'X') : "=";
        }

        return null;
    }

    private static String transformForModifiers(final String start, final int keymod, final char lastChar) {
        final int modifier;
        switch (keymod) {
            case KeyHandler.KEYMOD_SHIFT:
                modifier = 2;
                break;
            case KeyHandler.KEYMOD_ALT:
                modifier = 3;
                break;
            case (KeyHandler.KEYMOD_SHIFT | KeyHandler.KEYMOD_ALT):
                modifier = 4;
                break;
            case KeyHandler.KEYMOD_CTRL:
                modifier = 5;
                break;
            case KeyHandler.KEYMOD_SHIFT | KeyHandler.KEYMOD_CTRL:
                modifier = 6;
                break;
            case KeyHandler.KEYMOD_ALT | KeyHandler.KEYMOD_CTRL:
                modifier = 7;
                break;
            case KeyHandler.KEYMOD_SHIFT | KeyHandler.KEYMOD_ALT | KeyHandler.KEYMOD_CTRL:
                modifier = 8;
                break;
            default:
                return start + lastChar;
        }
        return start + (";" + modifier) + lastChar;
    }
}