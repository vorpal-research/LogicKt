package ru.spbstu.logic

import ru.spbstu.logic.lib.*

fun GoalScope.divModO(n: Expr, m: Expr, d: Expr, r: Expr) {
    scope {
        val n by bind(n)
        val m by bind(m)
        val d by bind(d)
        val r by bind(r)
        var tot by vars
        mulO(m, d, tot)
        plusO(tot, r, n)
    }
}

fun main(args: Array<String>) {
//    run {
//        val c = (1..3).iterator().toSStream()
//        val mu = c.mixMap { (0..it).iterator().toSStream() }
//        println(mu.toList())
//    }

    val res1 = goal {
        var a by vars
        var b by vars
        var c by vars
        var d by vars
        eqlO(a, b)
        //x = nat(3)

    }
    for (r in res1.take(50)) {
        println("---")
        for ((k, v) in r) {
            println("$k = ${reify(v)}")
        }
    }
}

