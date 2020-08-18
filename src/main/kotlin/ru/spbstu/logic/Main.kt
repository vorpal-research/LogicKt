package ru.spbstu.logic

import ru.spbstu.logic.lib.plusO
import ru.spbstu.logic.lib.reify

fun main(args: Array<String>) {
//    run {
//        val c = (1..3).iterator().toSStream()
//        val mu = c.mixMap { (0..it).iterator().toSStream() }
//        println(mu.toList())
//    }

    val res1 = goal {
        var x by vars
        var r by vars
        plusO(x, x, r)
    }
    for (r in res1.take(50)) {
        println("---")
        for ((k, v) in r) {
            println("$k = ${reify(v)}")
        }
    }
}

