package com.termux.terminal

import android.view.KeyEvent

object KeyHandler {
    const val KEYMOD_ALT: Int = -0x80000000

    const val KEYMOD_CTRL: Int = 0x40000000

    const val KEYMOD_SHIFT: Int = 0x20000000

    const val KEYMOD_NUM_LOCK: Int = 0x10000000

    private val TERMCAP_TO_KEYCODE: Map<String, Int> by lazy {
        mapOf(
            "%i" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_RIGHT),
            "#2" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_MOVE_HOME),
            "#4" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_LEFT),
            "*7" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_MOVE_END),
            "k1" to KeyEvent.KEYCODE_F1,
            "k2" to KeyEvent.KEYCODE_F2,
            "k3" to KeyEvent.KEYCODE_F3,
            "k4" to KeyEvent.KEYCODE_F4,
            "k5" to KeyEvent.KEYCODE_F5,
            "k6" to KeyEvent.KEYCODE_F6,
            "k7" to KeyEvent.KEYCODE_F7,
            "k8" to KeyEvent.KEYCODE_F8,
            "k9" to KeyEvent.KEYCODE_F9,
            "k;" to KeyEvent.KEYCODE_F10,
            "F1" to KeyEvent.KEYCODE_F11,
            "F2" to KeyEvent.KEYCODE_F12,
            "F3" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F1),
            "F4" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F2),
            "F5" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F3),
            "F6" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F4),
            "F7" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F5),
            "F8" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F6),
            "F9" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F7),
            "FA" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F8),
            "FB" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F9),
            "FC" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F10),
            "FD" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F11),
            "FE" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_F12),
            "kb" to KeyEvent.KEYCODE_DEL,
            "kd" to KeyEvent.KEYCODE_DPAD_DOWN,
            "kh" to KeyEvent.KEYCODE_MOVE_HOME,
            "kl" to KeyEvent.KEYCODE_DPAD_LEFT,
            "kr" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "K1" to KeyEvent.KEYCODE_MOVE_HOME,
            "K3" to KeyEvent.KEYCODE_PAGE_UP,
            "K4" to KeyEvent.KEYCODE_MOVE_END,
            "K5" to KeyEvent.KEYCODE_PAGE_DOWN,
            "ku" to KeyEvent.KEYCODE_DPAD_UP,
            "kB" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_TAB),
            "kD" to KeyEvent.KEYCODE_FORWARD_DEL,
            "kDN" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_DOWN),
            "kF" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_DOWN),
            "kI" to KeyEvent.KEYCODE_INSERT,
            "kN" to KeyEvent.KEYCODE_PAGE_UP,
            "kP" to KeyEvent.KEYCODE_PAGE_DOWN,
            "kR" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_UP),
            "kUP" to (KEYMOD_SHIFT or KeyEvent.KEYCODE_DPAD_UP),
            "@7" to KeyEvent.KEYCODE_MOVE_END,
            "@8" to KeyEvent.KEYCODE_NUMPAD_ENTER
        )
    }

    fun getCodeFromTermcap(
        termcap: String,
        cursorKeysApplication: Boolean,
        keypadApplication: Boolean
    ): String? {
        var keyCode = TERMCAP_TO_KEYCODE[termcap] ?: return null
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
        keyCode: Int, keyMode: Int, cursorApp: Boolean, keypadApplication: Boolean
    ): String? {
        val numLockOn = 0 != (keyMode and KEYMOD_NUM_LOCK)
        val keyMode1 = keyMode and KEYMOD_NUM_LOCK.inv()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> return "\u000d"
            KeyEvent.KEYCODE_DPAD_UP -> return if ((0 == keyMode1)) (if (cursorApp) "\u001bOA" else "\u001b[A") else transformForModifiers(
                "\u001b[1", keyMode1, 'A'
            )

            KeyEvent.KEYCODE_DPAD_DOWN -> return if ((0 == keyMode1)) (if (cursorApp) "\u001bOB" else "\u001b[B") else transformForModifiers(
                "\u001b[1", keyMode1, 'B'
            )

            KeyEvent.KEYCODE_DPAD_RIGHT -> return if ((0 == keyMode1)) (if (cursorApp) "\u001bOC" else "\u001b[C") else transformForModifiers(
                "\u001b[1", keyMode1, 'C'
            )

            KeyEvent.KEYCODE_DPAD_LEFT -> return if ((0 == keyMode1)) (if (cursorApp) "\u001bOD" else "\u001b[D") else transformForModifiers(
                "\u001b[1", keyMode1, 'D'
            )

            KeyEvent.KEYCODE_MOVE_HOME ->                 // Note that KEYCODE_HOME is handled by the system and never delivered to applications.
                // On a Logitech k810 keyboard KEYCODE_MOVE_HOME is sent by FN+LeftArrow.
                return if ((0 == keyMode1)) (if (cursorApp) "\u001bOH" else "\u001b[H") else transformForModifiers(
                    "\u001b[1", keyMode1, 'H'
                )

            KeyEvent.KEYCODE_MOVE_END -> return if ((0 == keyMode1)) (if (cursorApp) "\u001bOF" else "\u001b[F") else transformForModifiers(
                "\u001b[1", keyMode1, 'F'
            )

            KeyEvent.KEYCODE_F1 -> return if ((0 == keyMode1)) "\u001bOP" else transformForModifiers(
                "\u001b[1", keyMode1, 'P'
            )

            KeyEvent.KEYCODE_F2 -> return if ((0 == keyMode1)) "\u001bOQ" else transformForModifiers(
                "\u001b[1", keyMode1, 'Q'
            )

            KeyEvent.KEYCODE_F3 -> return if ((0 == keyMode1)) "\u001bOR" else transformForModifiers(
                "\u001b[1", keyMode1, 'R'
            )

            KeyEvent.KEYCODE_F4 -> return if ((0 == keyMode1)) "\u001bOS" else transformForModifiers(
                "\u001b[1", keyMode1, 'S'
            )

            KeyEvent.KEYCODE_F5 -> return transformForModifiers("\u001b[15", keyMode1, '~')
            KeyEvent.KEYCODE_F6 -> return transformForModifiers("\u001b[17", keyMode1, '~')
            KeyEvent.KEYCODE_F7 -> return transformForModifiers("\u001b[18", keyMode1, '~')
            KeyEvent.KEYCODE_F8 -> return transformForModifiers("\u001b[19", keyMode1, '~')
            KeyEvent.KEYCODE_F9 -> return transformForModifiers("\u001b[20", keyMode1, '~')
            KeyEvent.KEYCODE_F10 -> return transformForModifiers("\u001b[21", keyMode1, '~')
            KeyEvent.KEYCODE_F11 -> return transformForModifiers("\u001b[23", keyMode1, '~')
            KeyEvent.KEYCODE_F12 -> return transformForModifiers("\u001b[24", keyMode1, '~')
            KeyEvent.KEYCODE_SYSRQ ->                 // Sys Request / Print
                return "\u001b[32~"

            KeyEvent.KEYCODE_BREAK ->                 // Pause/Break
                return "\u001b[34~"

            KeyEvent.KEYCODE_ESCAPE -> return "\u001b"
            KeyEvent.KEYCODE_INSERT -> return transformForModifiers("\u001b[2", keyMode1, '~')
            KeyEvent.KEYCODE_FORWARD_DEL -> return transformForModifiers("\u001b[3", keyMode1, '~')
            KeyEvent.KEYCODE_PAGE_UP -> return transformForModifiers("\u001b[5", keyMode1, '~')
            KeyEvent.KEYCODE_PAGE_DOWN -> return transformForModifiers("\u001b[6", keyMode1, '~')
            KeyEvent.KEYCODE_DEL -> {
                val prefix = if ((0 == (keyMode1 and KEYMOD_ALT))) "" else "\u001b"
                // Just do what xterm and gnome-terminal does:
                return prefix + (if ((0 == (keyMode1 and KEYMOD_CTRL))) "\u007F" else "\u0008")
            }

            KeyEvent.KEYCODE_NUM_LOCK -> return if (keypadApplication) {
                "\u001bOP"
            } else {
                null
            }

            KeyEvent.KEYCODE_SPACE ->                 // If ctrl is not down, return null so that it goes through normal input processing (which may e.g. cause a
                // combining accent to be written):
                return if ((0 == (keyMode1 and KEYMOD_CTRL))) null else "\u0000"

            KeyEvent.KEYCODE_TAB ->                 // This is back-tab when shifted:
                return if (0 == (keyMode1 and KEYMOD_SHIFT)) "\u0009" else "\u001b[Z"

            KeyEvent.KEYCODE_ENTER -> return if ((0 == (keyMode1 and KEYMOD_ALT))) "\r" else "\u001b\r"
            KeyEvent.KEYCODE_NUMPAD_ENTER -> return if (keypadApplication) transformForModifiers(
                "\u001bO", keyMode1, 'M'
            ) else "\n"

            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> return if (keypadApplication) transformForModifiers(
                "\u001bO", keyMode1, 'j'
            ) else "*"

            KeyEvent.KEYCODE_NUMPAD_ADD -> return if (keypadApplication) transformForModifiers(
                "\u001bO", keyMode1, 'k'
            ) else "+"

            KeyEvent.KEYCODE_NUMPAD_COMMA -> return ","
            KeyEvent.KEYCODE_NUMPAD_DOT -> return if (numLockOn) {
                if (keypadApplication) "\u001bOn" else "."
            } else {
                // DELETE
                transformForModifiers("\u001b[3", keyMode1, '~')
            }

            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> return if (keypadApplication) transformForModifiers(
                "\u001bO", keyMode1, 'm'
            ) else "-"

            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> return if (keypadApplication) transformForModifiers(
                "\u001bO", keyMode1, 'o'
            ) else "/"

            KeyEvent.KEYCODE_NUMPAD_0 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 'p'
                ) else "0"
            } else {
                // INSERT
                transformForModifiers("\u001b[2", keyMode1, '~')
            }

            KeyEvent.KEYCODE_NUMPAD_1 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 'q'
                ) else "1"
            } else {
                // END
                if ((0 == keyMode1)) (if (cursorApp) "\u001bOF" else "\u001b[F") else transformForModifiers(
                    "\u001b[1", keyMode1, 'F'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_2 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 'r'
                ) else "2"
            } else {
                // DOWN
                if ((0 == keyMode1)) (if (cursorApp) "\u001bOB" else "\u001b[B") else transformForModifiers(
                    "\u001b[1", keyMode1, 'B'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_3 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 's'
                ) else "3"
            } else {
                // PGDN
                "\u001b[6~"
            }

            KeyEvent.KEYCODE_NUMPAD_4 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 't'
                ) else "4"
            } else {
                // LEFT
                if ((0 == keyMode1)) (if (cursorApp) "\u001bOD" else "\u001b[D") else transformForModifiers(
                    "\u001b[1", keyMode1, 'D'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_5 -> return if (keypadApplication) transformForModifiers(
                "\u001bO", keyMode1, 'u'
            ) else "5"

            KeyEvent.KEYCODE_NUMPAD_6 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 'v'
                ) else "6"
            } else {
                // RIGHT
                if ((0 == keyMode1)) (if (cursorApp) "\u001bOC" else "\u001b[C") else transformForModifiers(
                    "\u001b[1", keyMode1, 'C'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_7 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 'w'
                ) else "7"
            } else {
                // HOME
                if ((0 == keyMode1)) (if (cursorApp) "\u001bOH" else "\u001b[H") else transformForModifiers(
                    "\u001b[1", keyMode1, 'H'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_8 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 'x'
                ) else "8"
            } else {
                // UP
                if ((0 == keyMode1)) (if (cursorApp) "\u001bOA" else "\u001b[A") else transformForModifiers(
                    "\u001b[1", keyMode1, 'A'
                )
            }

            KeyEvent.KEYCODE_NUMPAD_9 -> return if (numLockOn) {
                if (keypadApplication) transformForModifiers(
                    "\u001bO", keyMode1, 'y'
                ) else "9"
            } else {
                // PGUP
                "\u001b[5~"
            }

            KeyEvent.KEYCODE_NUMPAD_EQUALS -> return if (keypadApplication) transformForModifiers(
                "\u001bO", keyMode1, 'X'
            ) else "="
        }
        return null
    }

    private fun transformForModifiers(start: String, keymod: Int, lastChar: Char) = when (keymod) {
        KEYMOD_SHIFT -> "$start;2$lastChar"
        KEYMOD_ALT -> "$start;3$lastChar"
        KEYMOD_SHIFT or KEYMOD_ALT -> "$start;4$lastChar"
        KEYMOD_CTRL -> "$start;5$lastChar"
        KEYMOD_SHIFT or KEYMOD_CTRL -> "$start;6$lastChar"
        KEYMOD_ALT or KEYMOD_CTRL -> "$start;7$lastChar"
        KEYMOD_SHIFT or KEYMOD_ALT or KEYMOD_CTRL -> "$start;8$lastChar"
        else -> "$start$lastChar"
    }

}
