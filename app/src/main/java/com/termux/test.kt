package com.termux

import kotlin.system.measureNanoTime

fun main() {
    println(measureNanoTime {
        println(57.toChar())
    })

    println(measureNanoTime {

    })

}
