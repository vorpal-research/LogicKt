package ru.spbstu.logic.lib

import ru.spbstu.logic.*

private typealias BitRep = Byte

val natZero by Symbol
val natBits by Function2
val zeroBit = Constant<BitRep>(0)
val oneBit = Constant<BitRep>(1)
val GoalScope.natOne: Expr get() = natBits[oneBit, natZero]

fun GoalScope.nat(number: Int): Expr = when {
    number == 0 -> natZero
    number % 2 == 0 -> natBits[zeroBit, nat(number / 2)]
    else -> natBits[oneBit, nat((number - 1) / 2)]
}

fun GoalScope.posO(n: Expr): Unit = scope {
    var n by bind(n)
    n = natBits[any(), any()]
}

fun GoalScope.aboveOneO(n: Expr): Unit = scope {
    var n by bind(n)
    n = natBits[any(), natBits[any(), any()]]
}

fun GoalScope.bitXorO(x: Expr, y: Expr, r: Expr) = table(x, y, r) { row: GoalScope.(BitRep, BitRep, BitRep) -> Unit ->
    row(0, 0, 0)
    row(0, 1, 1)
    row(1, 0, 1)
    row(1, 1, 0)
}

fun GoalScope.bitAndO(x: Expr, y: Expr, r: Expr) = table(x, y, r) { row: GoalScope.(BitRep, BitRep, BitRep) -> Unit ->
    row(0, 0, 0)
    row(1, 0, 0)
    row(0, 1, 0)
    row(1, 1, 1)
}

fun GoalScope.halfAdderO(x: Expr, y: Expr, r: Expr, c: Expr) = scope {
    val x by bind(x)
    val y by bind(y)
    val r by bind(r)
    val c by bind(c)
    bitXorO(x, y, r)
    bitAndO(x, y, c)
}

fun GoalScope.fullAdderO(b: Expr, x: Expr, y: Expr, r: Expr, c: Expr) = scope {
    val b by bind(b)
    val x by bind(x)
    val y by bind(y)
    val r by bind(r)
    val c by bind(c)
    val w by vars
    val xy by vars
    val wz by vars

    halfAdderO(x, y, w, xy)
    halfAdderO(w, b, r, wz)
    bitXorO(xy, wz, c)
}

fun GoalScope.adderO(d: Expr, n: Expr, m: Expr, r: Expr): Unit = scope {
    val d by bind(d)
    var n by bind(n)
    var m by bind(m)
    var r by bind(r)

    match(d, n, m) { row ->
        row(zeroBit, any(), natZero) {
            r = n
        }
        row(zeroBit, natZero, any()) {
            posO(m)
            r = m
        }
        row(oneBit, any(), natZero) {
            adderO(zeroBit, n, natOne, r)
        }
        row(oneBit, natZero, any()) {
            posO(m)
            adderO(zeroBit, natOne, m, r)
        }
        row(any(), natOne, natOne) {
            val a by vars
            val c by vars
            r = natBits[a, natBits[c, natZero]]
            fullAdderO(d, oneBit, oneBit, a, c)
        }
        row(any(), natOne, any()) {
            genAdderO(d, n, m, r)
        }
        row(any(), any(), natOne) {
            aboveOneO(n)
            aboveOneO(r)
            adderO(d, natOne, n, r)
        }
        row(any(), any(), any()) {
            aboveOneO(n)
            genAdderO(d, n, m, r)
        }
    }
}

fun GoalScope.genAdderO(d: Expr, n: Expr, m: Expr, r: Expr): Unit = scope {
    val d by bind(d)
    val n by bind(n)
    val m by bind(m)
    val r by bind(r)
    val a by vars
    val x by vars
    val b by vars
    val y by vars
    val c by vars
    val z by vars
    natBits[a, x] = n
    natBits[b, y] = m
    natBits[c, z] = r
    val e by vars

    posO(y)
    posO(z)
    fullAdderO(d, a, b, c, e)
    adderO(e, x, y, z)
}

fun GoalScope.plusO(n: Expr, m: Expr, k: Expr) = scope {
    val n by bind(n)
    val m by bind(m)
    val k by bind(k)
    adderO(zeroBit, n, m, k)
}

fun GoalScope.minusO(n: Expr, m: Expr, k: Expr) = plusO(k, m, n)
