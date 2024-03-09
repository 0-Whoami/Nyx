package com

import kotlin.system.measureNanoTime

fun main() {
    val map by lazy { HashMap<String, String>() }
    val map1 = HashMap<String, String>()
    println(measureNanoTime {
        for (i in 0..1000000)
            map.clear()
    })
    println(measureNanoTime {
        for (i in 0..1000000)
            map1.clear()
    })
}
