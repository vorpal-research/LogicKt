package ru.spbstu.logic.lib

import ru.spbstu.logic.*

val nil by Symbol
val cons by Function2

fun GoalScope.lst(vararg elements: Any) =
    elements.foldRight(nil as Expr) { e, l -> cons[e, l] }

fun GoalScope.appendO(l: Expr, s: Expr, out: Expr): Unit = scope {
    var l by bind(l)
    var s by bind(s)
    var out by bind(out)
    choose(
        {
            l = nil
            s = out
        },
        {
            val a by vars
            val d by vars

            l = cons[a, d]

            val res by vars
            appendO(d, s, res)
            out = cons[a, res]
        }
    )
}

fun GoalScope.consO(h: Expr, t: Expr, out: Expr) = scope {
    val h by bind(h)
    val t by bind(t)
    var out by bind(out)
    out = cons[h, t]
}

fun GoalScope.headO(l: Expr, out: Expr) = consO(out, any(), l)
fun GoalScope.tailO(l: Expr, out: Expr) = consO(any(), out, l)

fun GoalScope.tailsO(l: Expr, out: Expr): Unit = scope {
    var l by bind(l)
    var out by bind(out)
    choose({
        out = l
    }, {
        var t by vars
        l = cons[any(), t]
        tailsO(t, out)
    })
}

fun GoalScope.lastO(l: Expr, out: Expr): Unit = tailsO(l, cons[out, nil])
fun GoalScope.unconsO(l: Expr, outh: Expr, outt: Expr): Unit = scope {
    var l by bind(l)
    var outt by bind(outt)
    var outh by bind(outh)

    choose(
        {
            outt = nil
            l = cons[outh, nil]
        },
        {
            val h by vars
            val it by vars
            val t by vars
            outt = cons[h, it]
            l = cons[h, t]
            unconsO(t, outh, it)
        }
    )

}

fun GoalScope.revertO(l: Expr, out: Expr): Unit = scope {
    var l by bind(l)
    var out by bind(out)
    choose(
        { l = nil; out = nil },
        {
            l = cons[any(), nil]
            out = l
        },
        {
            var h by vars
            var t by vars
            var rt by vars

            l = cons[h, t]
            unconsO(out, h, rt)
            revertO(t, rt)
        }
    )
}

fun GoalScope.lengthIO(l: Expr, out: Expr): Unit = scope {
    var l by bind(l)
    var out by bind(out)
    choose(
        {
            l = nil; out = Constant(0)
        },
        {
            val t by vars
            val tlen by vars
            l = cons[any(), t]
            out = inc[tlen]
            lengthIO(t, tlen)
        }
    )
}

fun GoalScope.lengthO(l: Expr, out: Expr): Unit = scope {
    var l by bind(l)
    var out by bind(out)
    choose(
        {
            l = nil; out = natZero
        },
        {
            val t by vars
            val tlen by vars
            l = cons[any(), t]
            lengthO(t, tlen)
            plusO(tlen, natOne, out)
        }
    )
}

