package com.termux.utils.data

import java.io.File

class Properties(file_path: String) {
    private val map = mutableMapOf<String, String>()
    private val file = File(file_path)

    init {
        if (file.exists()) {
            file.forEachLine { line ->
                line.split(" ").let {
                    map[it[0]] = it[1]
                }
            }
        }
    }

    fun forEach(action: (key: String, value: String) -> Unit) = map.forEach(action)
    fun getInt(key: String, default: Int): Int =
        if (map.containsKey(key)) map[key]!!.toInt() else default

    fun getBoolean(key: String, default: Boolean): Boolean =
        if (map.containsKey(key)) map[key]!!.toBoolean() else default
}
