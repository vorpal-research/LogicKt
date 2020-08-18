package ru.spbstu.logic

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.plus

class System(
    val solution: PersistentDisjointSet<Expr> = PersistentDisjointSet(),
    val varSet: PersistentSet<Var> = persistentHashSetOf()
) {

    fun equals(lhv: Expr, rhv: Expr): System? {
        val lhv = solution.find(lhv)
        val rhv = solution.find(rhv)
        return when {
            lhv is Invalid || rhv is Invalid -> null
            // eliminate
            lhv == rhv -> this
            lhv is Constant<*> && rhv is Constant<*> -> null /* not equal by previous case */
            lhv is App && rhv is App -> {
                when {
                    // decompose
                    lhv.f == rhv.f && lhv.args.size == rhv.args.size ->
                        lhv.args.zip(rhv.args).fold(this ?: null) { self, (a, b) -> self?.equals(a, b) }
                    // conflict
                    else -> null
                }
            }
            lhv is Constant<*> && rhv is ProjectedApp -> {
                equals(rhv.v, Constant(rhv.f.uninvoke(lhv.value)))
            }
            rhv is Constant<*> && lhv is ProjectedApp -> equals(rhv, lhv)
            lhv is ProjectedApp && rhv is ProjectedApp -> {
                if (lhv.f == rhv.f) {
                    equals(lhv.v, rhv.v)
                } else {
                    equals(lhv.v, ProjectedApp(lhv.f.inverse + rhv.f, rhv.v))
                    equals(rhv.v, ProjectedApp(rhv.f.inverse + lhv.f, lhv.v))
                }
            }
            lhv !is Var && rhv !is Var -> null
            rhv is Var && lhv !is Var -> equals(rhv, lhv)
            lhv is Var -> {
                System(
                    solution.unionForced(lhv, rhv),
                    varSet + lhv
                )
            }
            else -> this
        }
    }

    fun expand(
        value: Expr,
        substitutions: MutableMap<Expr, Var> = mutableMapOf(),
        renaming: MutableMap<Var, Var> = mutableMapOf(),
        marked: MutableSet<Var> = mutableSetOf(),
        cache: MutableMap<Expr, Expr> = mutableMapOf(),
        nesting: Int = 0
    ): Expr = cache.getOrPut(value) {
        when (value) {
            in substitutions -> {
                val vv = substitutions[value]!!
                marked.add(vv)
                vv
            }
            is Constant<*>, is MuExpr, is Invalid -> value
            is Var -> {
                val vv = solution.find(value)
                if (vv != value) expand(vv, substitutions, renaming, marked, cache, nesting)
                else renaming.getOrPut(value) { TempVar() }
            }
            is AppBase -> {
                val maybeAlpha = BoundVar(nesting + 1)
                substitutions[value] = maybeAlpha
                val freshCache = cache.toMutableMap()
                val maybeNewArgs = value.args.map { expand(it, substitutions, renaming, marked, freshCache, nesting + 1) }
                var res = value
                if (!(maybeNewArgs identityEquals value.args)) res = res.copy(args = maybeNewArgs)!!
                if (maybeAlpha in marked) {
                    res = MuExpr(maybeAlpha, res)
                    freshCache.clear()
                }
                cache.putAll(freshCache)
                marked.remove(maybeAlpha)
                substitutions.remove(value)
                res
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        val cache = mutableMapOf<Expr, Expr>()
        for (v in varSet) {
            sb.append("$v = ")
            sb.append(expand(v, cache = cache))
            sb.appendln()
        }
        return "$sb"
    }
}