package ru.spbstu.logic.lib

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import ru.spbstu.logic.App
import ru.spbstu.logic.Constant
import ru.spbstu.logic.Expr
import java.math.BigInteger

fun reifyAsNumber(expr: Expr): BigInteger? = run {
    when (expr) {
        natZero -> BigInteger.ZERO
        is Constant<*> -> expr.value.toString().toBigIntegerOrNull()
        is App -> when (expr.f) {
            natBits -> {
                val base = reifyAsNumber(expr.args[1]) ?: return null
                val digit = reifyAsNumber(expr.args[0]) ?: return null
                digit + base * 2.toBigInteger()
            }
            else -> null
        }
        else -> null
    }
}

fun reifyAsList(expr: Expr): PersistentList<*>? = run {
    when (expr) {
        natZero, nil -> persistentListOf<Any?>()
        is App -> when (expr.f) {
            natBits, cons -> {
                val tail = reifyAsList(expr.args[1]) ?: return null
                persistentListOf(reify(expr.args[0])) + tail
            }
            else -> null
        }
        else -> null
    }
}

fun reify(expr: Expr): Any? = reifyAsNumber(expr) ?: reifyAsList(expr) ?: expr