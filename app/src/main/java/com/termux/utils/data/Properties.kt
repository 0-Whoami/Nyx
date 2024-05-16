package com.termux.utils.data

import java.io.File

class Properties(filePath: String) {
    private val map = mutableMapOf<String, String>()


    init {
        val file = File(filePath)
        if (file.exists()) {
            file.forEachLine { line ->
                line.split(" : ").let { strings ->
                    if (strings.size != 2) return@let
                    map[strings[0]] = strings[1]
                }
            }
        }
    }

    fun forEach(action: (key: String, value: String) -> Unit): Unit = map.forEach(action)
    fun getInt(key: String, default: Int): Int =
        if (map.containsKey(key)) map[key]!!.toInt() else default

    fun getBoolean(key: String, default: Boolean): Boolean =
        if (map.containsKey(key)) map[key]!!.toBoolean() else default
}
