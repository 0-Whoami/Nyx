package com.termux.terminal

import android.view.KeyEvent

object KeyHandler {
    const val KEYMOD_ALT: Int = -0x80000000

    const val KEYMOD_CTRL: Int = 0x40000000

    const val KEYMOD_SHIFT: Int = 0x20000000

    const val KEYMOD_NUM_LOCK: Int = 0x10000000

    private val TERMCAP_TO_KEYCODE: MutableMap<String, Int> by lazy { HashMap() }

    init {
        // terminfo: http://pubs.opengroup.org/onlinepubs/7990989799/xcurses/terminfo.html
        // termcap: http://man7.org/linux/man-pages/man5/termcap.5.html
        TERMCAP_TO_KEYCODE["%i"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_RIGHT
        // Shifted home
        TERMCAP_TO_KEYCODE["#2"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_MOVE_HOME
        TERMCAP_TO_KEYCODE["#4"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_LEFT
        // Shifted end key
        TERMCAP_TO_KEYCODE["*7"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_MOVE_END
        TERMCAP_TO_KEYCODE["k1"] = KeyEvent.KEYCODE_F1
        TERMCAP_TO_KEYCODE["k2"] = KeyEvent.KEYCODE_F2
        TERMCAP_TO_KEYCODE["k3"] = KeyEvent.KEYCODE_F3
        TERMCAP_TO_KEYCODE["k4"] = KeyEvent.KEYCODE_F4
        TERMCAP_TO_KEYCODE["k5"] = KeyEvent.KEYCODE_F5
        TERMCAP_TO_KEYCODE["k6"] = KeyEvent.KEYCODE_F6
        TERMCAP_TO_KEYCODE["k7"] = KeyEvent.KEYCODE_F7
        TERMCAP_TO_KEYCODE["k8"] = KeyEvent.KEYCODE_F8
        TERMCAP_TO_KEYCODE["k9"] = KeyEvent.KEYCODE_F9
        TERMCAP_TO_KEYCODE["k;"] = KeyEvent.KEYCODE_F10
        TERMCAP_TO_KEYCODE["F1"] = KeyEvent.KEYCODE_F11
        TERMCAP_TO_KEYCODE["F2"] = KeyEvent.KEYCODE_F12
        TERMCAP_TO_KEYCODE["F3"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F1
        TERMCAP_TO_KEYCODE["F4"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F2
        TERMCAP_TO_KEYCODE["F5"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_F3
        TERMCAP_TO_KEYCODE["F6"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_F4
        TERMCAP_TO_KEYCODE["F7"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_F5
        TERMCAP_TO_KEYCODE["F8"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F6
        TERMCAP_TO_KEYCODE["F9"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F7
        TERMCAP_TO_KEYCODE["FA"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F8
        TERMCAP_TO_KEYCODE["FB"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_F9
        TERMCAP_TO_KEYCODE["FC"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F10
        TERMCAP_TO_KEYCODE["FD"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F11
        TERMCAP_TO_KEYCODE["FE"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_F12
        // backspace key
        TERMCAP_TO_KEYCODE["kb"] = KeyEvent.KEYCODE_DEL
        // terminfo=kcud1, down-arrow key
        TERMCAP_TO_KEYCODE["kd"] =
            KeyEvent.KEYCODE_DPAD_DOWN
        TERMCAP_TO_KEYCODE["kh"] =
            KeyEvent.KEYCODE_MOVE_HOME
        TERMCAP_TO_KEYCODE["kl"] =
            KeyEvent.KEYCODE_DPAD_LEFT
        TERMCAP_TO_KEYCODE["kr"] =
            KeyEvent.KEYCODE_DPAD_RIGHT
        // K1=Upper left of keypad:
        // t_K1 <kHome> keypad home key
        // t_K3 <kPageUp> keypad page-up key
        // t_K4 <kEnd> keypad end key
        // t_K5 <kPageDown> keypad page-down key
        TERMCAP_TO_KEYCODE["K1"] =
            KeyEvent.KEYCODE_MOVE_HOME
        TERMCAP_TO_KEYCODE["K3"] = KeyEvent.KEYCODE_PAGE_UP
        TERMCAP_TO_KEYCODE["K4"] = KeyEvent.KEYCODE_MOVE_END
        TERMCAP_TO_KEYCODE["K5"] = KeyEvent.KEYCODE_PAGE_DOWN
        TERMCAP_TO_KEYCODE["ku"] = KeyEvent.KEYCODE_DPAD_UP
        // termcap=kB, terminfo=kcbt: Back-tab
        TERMCAP_TO_KEYCODE["kB"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_TAB
        // terminfo=kdch1, delete-character key
        TERMCAP_TO_KEYCODE["kD"] = KeyEvent.KEYCODE_FORWARD_DEL
        // non-standard shifted arrow down
        TERMCAP_TO_KEYCODE["kDN"] =
            KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_DOWN
        // terminfo=kind, scroll-forward key
        TERMCAP_TO_KEYCODE["kF"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_DOWN
        TERMCAP_TO_KEYCODE["kI"] = KeyEvent.KEYCODE_INSERT
        TERMCAP_TO_KEYCODE["kN"] = KeyEvent.KEYCODE_PAGE_UP
        TERMCAP_TO_KEYCODE["kP"] =
            KeyEvent.KEYCODE_PAGE_DOWN
        // terminfo=kri, scroll-backward key
        TERMCAP_TO_KEYCODE["kR"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_UP
        // non-standard shifted up
        TERMCAP_TO_KEYCODE["kUP"] = KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_UP
        TERMCAP_TO_KEYCODE["@7"] = KeyEvent.KEYCODE_MOVE_END
        TERMCAP_TO_KEYCODE["@8"] = KeyEvent.KEYCODE_NUMPAD_ENTER
    }


    fun getCodeFromTermcap(
        termcap: String,
        cursorKeysApplication: Boolean,
        keypadApplication: Boolean
    ): String? {
        val keyCodeAndMod = TERMCAP_TO_KEYCODE[termcap] ?: return null
        var keyCode = keyCodeAndMod
        var keyMod = 0
        if (0 != (keyCode and KEYMOD_SHIFT)) {
            keyMod = keyMod or KEYMOD_SHIFT
            keyCode = keyCode and KEYMOD_SHIFT.inv()
        }
        if (0 != (keyCode and KEYMOD_CTRL)) {
            keyMod = keyMod or KEYMOD_CTRL
            keyCode = keyCode and KEYMOD_CTRL.inv()
        }
        if (0 != (keyCode and KEYMOD_ALT)) {
            keyMod = keyMod or KEYMOD_ALT
            keyCode = keyCode and KEYMOD_ALT.inv()
        }
        if (0 != (keyCode and KEYMOD_NUM_LOCK)) {
            keyMod = keyMod or KEYMOD_NUM_LOCK
            keyCode = keyCode and KEYMOD_NUM_LOCK.inv()
        }
        return getCode(keyCode, keyMod, cursorKeysApplication, keypadApplication)
    }


    fun getCode(
        keyCode: Int,
        keyMode: Int,
        cursorApp: Boolean,
        keypadApplication: Boolean
    ): String? {
        var keyMode = keyMode
        val numLockOn = 0 != (keyMode and KEYMOD_NUM_LOCK)
        keyMode = keyMode and KEYMOD_NUM_LOCK.inv()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> return "\u000d"
            KeyEvent.KEYCODE_DPAD_UP -> return if ((0 == keyMode)) (if (cursorApp) "\u001bOA" else "\u001b[A") else transformForModifiers(
                "\u001b[1",
                keyMode,
                'A'
            )

            KeyEvent.KEYCODE_DPAD_DOWN -> return if ((0 == keyMode)) (if (cursorApp) "\u001bOB" else "\u001b[B") else transformForModifiers(
                "\u001b[1",
                keyMode,
                'B'
            )

            KeyEvent.KEYCODE_DPAD_RIGHT -> return if ((0 == keyMode)) (if (cursorApp) "\u001bOC" else "\u001b[C") else transformForModifiers(
                "\u001b[1",
                keyMode,
                'C'
            )

            KeyEvent.KEYCODE_DPAD_LEFT -> return if ((0 == keyMode)) (if (cursorApp) "\u001bOD" else "\u001b[D") else transformForModifiers(
                "\u001b[1",
                keyMode,
                'D'
            )

            KeyEvent.KEYCODE_MOVE_HOME ->                 // Note that KEYCODE_HOME is handled by the system and never delivered to applications.
                // On a Logitech k810 keyboard KEYCODE_MOVE_HOME is sent by FN+LeftArrow.
                return if ((0 == keyMode)) (if (cursorApp) "\u001bOH" else "\u001b[H") else transformForModifiers(
                    "\u001b[1",
                    keyMode,
                    'H'
                )

            KeyEvent.KEYCODE_MOVE_END -> return if ((0 == keyMode)) (if (cursorApp) "\u001bOF" else "\u001b[F") else transformForModifiers(
                "\u001b[1",
                keyMode,
                'F'
            )

            KeyEvent.KEYCODE_F1 -> return if ((0 == keyMode)) "\u001bOP" else transformForModifiers(
                "\u001b[1",
                keyMode,
                'P'
            )

            KeyEvent.KEYCODE_F2 -> return if ((0 == keyMode)) "\u001bOQ" else transformForModifiers(
                "\u001b[1",
                keyMode,
                'Q'
            )

            KeyEvent.KEYCODE_F3 -> return if ((0 == keyMode)) "\u001bOR" else transformForModifiers(
                "\u001b[1",
                keyMode,
                'R'
            )

            KeyEvent.KEYCODE_F4 -> return if ((0 == keyMode)) "\u001bOS" else transformForModifiers(
                "\u001b[1",
                keyMode,
                'S'
            )

            KeyEvent.KEYCODE_F5 -> return transformForModifiers("\u001b[15", keyMode, '~')
            KeyEvent.KEYCODE_F6 -> return transformForModifiers("\u001b[17", keyMode, '~')
            KeyEvent.KEYCODE_F7 -> return transformForModifiers("\u001b[18", keyMode, '~')
            KeyEvent.KEYCODE_F8 -> return transformForModifiers("\u001b[19", keyMode, '~')
            KeyEvent.KEYCODE_F9 -> return transformForModifiers("\u001b[20", keyMode, '~')
            KeyEvent.KEYCODE_F10 -> return transformForModifiers("\u001b[21", keyMode, '~')
            KeyEvent.KEYCODE_F11 -> return transformForModifiers("\u001b[23", keyMode, '~')
            KeyEvent.KEYCODE_F12 -> return transformForModifiers("\u001b[24", keyMode, '~')
            KeyEvent.KEYCODE_SYSRQ ->                 // Sys Request / Print
                return "\u001b[32~"

            KeyEvent.KEYCODE_BREAK ->                 // Pause/Break
                return "\u001b[34~"

            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> return "\u001b"
            KeyEvent.KEYCODE_INSERT -> return transformForModifiers("\u001b[2", keyMode, '~')
            KeyEvent.KEYCODE_FORWARD_DEL -> return transformForModifiers("\u001b[3", keyMode, '~')
            KeyEvent.KEYCODE_PAGE_UP -> return transformForModifiers("\u001b[5", keyMode, '~')
            KeyEvent.KEYCODE_PAGE_DOWN -> return transformForModifiers("\u001b[6", keyMode, '~')
            KeyEvent.KEYCODE_DEL -> {
                val prefix = if ((0 == (keyMode and KEYMOD_ALT))) "" else "\u001b"
                // Just do what xterm and gnome-terminal does:
                return prefix + (if ((0 == (keyMode and KEYMOD_CTRL))) "\u007F" else "\u0008")
            }

            KeyEvent.KEYCODE_NUM_LOCK -> return if (keypadApplication) {
                "\u001bOP"
            } else {
                null
            }

            KeyEvent.KEYCODE_SPACE ->                 // If ctrl is not down, return null so that it goes through normal input processing (which may e.g. cause a
                // combining accent to be written):
                return if ((0 == (keyMode and KEYMOD_CTRL))) null else "\u0000"

            KeyEvent.KEYCODE_TAB ->                 // This is back-tab when shifted:
                return if (0 == (keyMode and KEYMOD_SHIFT)) "\u0009" else "\u001b[Z"

            KeyEvent.KEYCODE_ENTER -> return if ((0 == (keyMode and KEYMOD_ALT))) "\r" else "\u001b\r"
            KeyEvent.KEYCODE_NUMPAD_ENTER -> return if (keypadApplication) transformForModifiers(
                "\u001bO",
                keyMode,
                'M'
            ) else "\n"

            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> return if (keypadApplication) transformForModifiers(
                "\u001bO",
                keyMode,
                'j'
            ) else "*"

            KeyEvent.KEYCODE_NUMPAD_ADD -> return if (keypadApplication) transformForModifiers(
                "\u001bO",
                keyMode,
                'k'
            ) else "+"

            KeyEvent.KEYCODE_NUMPAD_COMMA -> return ","
            KeyEvent.KEYCODE_NUMPAD_DOT -> return if (numLockOn) {
                if (keypadApplication) "\u001bOn" else "."
            } else {
                // DELETE
                transformForModifiers("\u001b[3", keyMode, '~')
            }

            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> return if (keypadApplication) transformForModifiers(
                "\u001bO",
                keyMode,
                'm'
            ) else "-"

            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> return if (keypadApplication) transformForModifiers(
                "\u001bO",
                keyMode,
                'o'
            ) else "/"

            KeyEvent.KEYCODE_NUMPAD_0 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    'p'
                ) else "0"
            } else {
                // INSERT
                transformForModifiers("\u001b[2", keyMode, '~')
            }

            KeyEvent.KEYCODE_NUMPAD_1 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    'q'
                ) else "1"
            } else {
                // END
                if ((0 == keyMode)) (if (cursorApp) "\u001bOF" else "\u001b[F") else transformForModifiers(
                    "\u001b[1",
                    keyMode,
                    'F'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_2 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    'r'
                ) else "2"
            } else {
                // DOWN
                if ((0 == keyMode)) (if (cursorApp) "\u001bOB" else "\u001b[B") else transformForModifiers(
                    "\u001b[1",
                    keyMode,
                    'B'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_3 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    's'
                ) else "3"
            } else {
                // PGDN
                "\u001b[6~"
            }

            KeyEvent.KEYCODE_NUMPAD_4 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    't'
                ) else "4"
            } else {
                // LEFT
                if ((0 == keyMode)) (if (cursorApp) "\u001bOD" else "\u001b[D") else transformForModifiers(
                    "\u001b[1",
                    keyMode,
                    'D'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_5 -> return if (keypadApplication) transformForModifiers(
                "\u001bO",
                keyMode,
                'u'
            ) else "5"

            KeyEvent.KEYCODE_NUMPAD_6 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    'v'
                ) else "6"
            } else {
                // RIGHT
                if ((0 == keyMode)) (if (cursorApp) "\u001bOC" else "\u001b[C") else transformForModifiers(
                    "\u001b[1",
                    keyMode,
                    'C'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_7 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    'w'
                ) else "7"
            } else {
                // HOME
                if ((0 == keyMode)) (if (cursorApp) "\u001bOH" else "\u001b[H") else transformForModifiers(
                    "\u001b[1",
                    keyMode,
                    'H'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_8 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    'x'
                ) else "8"
            } else {
                // UP
                if ((0 == keyMode)) (if (cursorApp) "\u001bOA" else "\u001b[A") else transformForModifiers(
                    "\u001b[1",
                    keyMode,
                    'A'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_9 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO",
                    keyMode,
                    'y'
                ) else "9"
            } else {
                // PGUP
                "\u001b[5~"
            }

            KeyEvent.KEYCODE_NUMPAD_EQUALS -> return if (keypadApplication) transformForModifiers(
                "\u001bO",
                keyMode,
                'X'
            ) else "="
        }
        return null
    }

    private fun transformForModifiers(start: String, keymod: Int, lastChar: Char): String {
        val modifier = when (keymod) {
            KEYMOD_SHIFT -> 2
            KEYMOD_ALT -> 3
            KEYMOD_SHIFT or KEYMOD_ALT -> 4
            KEYMOD_CTRL -> 5
            KEYMOD_SHIFT or KEYMOD_CTRL -> 6
            KEYMOD_ALT or KEYMOD_CTRL -> 7
            KEYMOD_SHIFT or KEYMOD_ALT or KEYMOD_CTRL -> 8
            else -> return start + lastChar
        }
        return "$start;$modifier$lastChar"
    }
}
