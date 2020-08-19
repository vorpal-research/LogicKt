package ru.spbstu.logic.lib

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import ru.spbstu.logic.App
import ru.spbstu.logic.Expr
import java.math.BigInteger

fun reify(expr: Expr): Any? = when (expr) {
    natZero -> 0.toBigInteger()
    nil -> persistentListOf<Expr>()
    is App -> when (expr.f) {
        natBits -> try {
            val base = reify(expr.args[1]) as BigInteger
            val cc = if (expr.args[0] == oneBit) 1.toBigInteger() else 0.toBigInteger()
            cc + base * 2.toBigInteger()
        } catch (e: Exception) {
            expr.toString()
        }
        cons -> try {
            val base = reify(expr.args[1]) as PersistentList<*>
            persistentListOf(reify(expr.args[0])) + base
        } catch (e: Exception) {
            expr.toString()
        }
        else -> expr.toString()
    }
    else -> expr.toString()
}
