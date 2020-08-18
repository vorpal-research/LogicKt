package ru.spbstu

import kotlinx.collections.immutable.*
import ru.spbstu.wheels.Stack
import ru.spbstu.wheels.stack
import java.lang.Exception
import kotlin.reflect.KProperty

class System(
    val solution: PersistentDisjointSet<Expr> = PersistentDisjointSet(),
    val varSet: PersistentSet<Var> = persistentHashSetOf()
) {

    fun equals(lhv: Expr, rhv: Expr): System? {
        val lhv = solution.find(lhv)
        val rhv = solution.find(rhv)
        return when {
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

    companion object {
        private var counter = 0
        private fun freshVarName() = "α${++counter}"
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
            is Constant<*>, is MuExpr -> value
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
                if (!(maybeNewArgs identityEquals value.args)) res = res.copy(args = maybeNewArgs) ?: Constant(null)
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

    fun fullSolution(
        value: Expr, app: StringBuilder,
        substitutions: MutableMap<Expr, Var> = mutableMapOf(),
        marked: MutableSet<Var> = mutableSetOf(),
        nesting: Int = 0
    ) {
        when (value) {
            in substitutions -> {
                val vv = substitutions[value]!!
                app.append(vv)
                marked.add(vv)
            }
            is Var -> {
                val vv = solution.find(value)
                if (vv != value) fullSolution(solution.find(value), app, substitutions, marked, nesting)
                else app.append(value.name)
            }
            is App -> {
                val vv = "α${nesting + 1}"
                substitutions[value] = ScopeVar(vv, nesting)
                val mupos = app.length

                app.append(value.f.name)
                app.append("[")
                val it = value.args.iterator()
                if (it.hasNext()) {
                    fullSolution(it.next(), app, substitutions, marked, nesting + 1)
                }
                for (arg in it) {
                    app.append(", ")
                    fullSolution(arg, app, substitutions, marked, nesting + 1)
                }
                app.append("]")
                if (ScopeVar(vv, nesting) in marked) app.insert(mupos, "μ $vv -> ")
                marked.remove(ScopeVar(vv, nesting))
                substitutions.remove(value)
            }
            is Constant<*> -> app.append(value)
        }

    }

    fun fullSolution(e: Expr) = buildString {
        fullSolution(e, this)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (v in varSet) {
            sb.append("$v = ")
            fullSolution(v, sb, mutableMapOf())
            sb.appendln()
        }
        return "$sb"
    }

    fun copy() = System(solution.copy(), varSet)
}

@DslMarker
annotation class GoalDSL

@GoalDSL
class GoalScope(
    var currentSolutions: SStream<System>,
    var variables: PersistentMap<String, Var>,
    val scopeStack: Stack<GoalScope>
) {

    companion object {
        var ids = 0
        fun freshId() = ++ids
    }

    val id = freshId()

    init {
        scopeStack.push(this)
    }

    val currentScope: GoalScope
        get() = scopeStack.top ?: this

    fun equals(lhv: Expr, rhv: Expr) {
        currentScope.currentSolutions = currentScope.currentSolutions.mapNotNull { it.equals(lhv, rhv) }
    }

    fun never() {
        currentScope.currentSolutions = SSNil
    }

    inline fun <T> fork(solutions: SStream<System>, crossinline body: GoalScope.() -> T): SStream<System> {
        if (solutions is SSNil) return SSNil
        check(scopeStack.size < 1000)
        val freshScope = GoalScope(solutions, currentScope.variables, scopeStack)
        freshScope.body()
        scopeStack.pop()
        return freshScope.currentSolutions
    }

    @GoalDSL
    inline fun scope(crossinline body: GoalScope.() -> Unit) {
        currentSolutions = fork(currentSolutions, body)
    }

    @GoalDSL
    inline fun choose(vararg goals: GoalScope.() -> Unit) {
        if (currentSolutions is SSNil) return
        val it = goals.iterator()
        val base = currentSolutions

        currentSolutions = it.toSStream().mixMap {
            fork(base, it)
        }
    }

    @GoalDSL
    fun not(body: GoalScope.() -> Unit) {
        val solutions = fork(currentSolutions, body)
        if (solutions !is SSNil) currentSolutions = SSNil
    }

    object VarsDelegate
    inner class BindingDelegate(val expr: Expr?)

    val vars = BindingDelegate(null)

    private fun bindVariable(name: String): Var {
        check(this@GoalScope === currentScope)
        val v = ScopeVar(name, id)
        variables += (v.name to v)
        return v
    }

    operator fun BindingDelegate.provideDelegate(self: Any?, prop: KProperty<*>): VarsDelegate {
        val v = bindVariable(prop.name)
        if (expr != null) equals(v, expr)
        return VarsDelegate
    }

    operator fun VarsDelegate.getValue(self: Any?, prop: KProperty<*>): Expr =
        variables.getValue(prop.name)

    operator fun VarsDelegate.setValue(self: Any?, prop: KProperty<*>, rhv: Expr) {
        equals(variables.getValue(prop.name), rhv)
    }

    fun bind(e: Expr): BindingDelegate = BindingDelegate(e)

    fun any(): Expr = TempVar()

    operator fun Function1.get(arg1: Expr) = App(this, arg1)
    operator fun Function2.get(arg1: Expr, arg2: Expr) = App(this, arg1, arg2)
    operator fun Function3.get(arg1: Expr, arg2: Expr, arg3: Expr) = App(this, arg1, arg2, arg3)
    operator fun Function4.get(arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr) = App(this, arg1, arg2, arg3, arg4)

    operator fun Function1.set(arg1: Expr, value: Expr) {
        equals(get(arg1), value)
    }

    operator fun Function2.set(arg1: Expr, arg2: Expr, value: Expr) {
        equals(get(arg1, arg2), value)
    }

    operator fun Function3.set(arg1: Expr, arg2: Expr, arg3: Expr, value: Expr) {
        equals(get(arg1, arg2, arg3), value)
    }

    operator fun Function4.set(arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, value: Expr) {
        equals(get(arg1, arg2, arg3, arg4), value)
    }

    fun asExpr(value: Any) = if (value is Expr) value else Constant(value)

    operator fun Function1.get(arg1: Any) =
        App(this, asExpr(arg1))

    operator fun Function2.get(arg1: Any, arg2: Any) =
        App(this, asExpr(arg1), asExpr(arg2))

    operator fun Function3.get(arg1: Any, arg2: Any, arg3: Any) =
        App(this, asExpr(arg1), asExpr(arg2), asExpr(arg3))

    operator fun Function4.get(arg1: Any, arg2: Any, arg3: Any, arg4: Any) =
        App(this, asExpr(arg1), asExpr(arg2), asExpr(arg3), asExpr(arg4))

    operator fun Function1.set(arg1: Any, value: Expr) {
        equals(get(arg1), value)
    }

    operator fun Function2.set(arg1: Any, arg2: Any, value: Expr) {
        equals(get(arg1, arg2), value)
    }

    operator fun Function3.set(arg1: Any, arg2: Any, arg3: Any, value: Expr) {
        equals(get(arg1, arg2, arg3), value)
    }

    operator fun Function4.set(arg1: Any, arg2: Any, arg3: Any, arg4: Any, value: Expr) {
        equals(get(arg1, arg2, arg3, arg4), value)
    }

    operator fun ProjectedFunction1.get(arg: Any) =
        ProjectedApp(this, asExpr(arg)) ?: Constant(null)
}

@GoalDSL
fun GoalScope.table(
    arg1: Expr,
    tabler: (row: GoalScope.(Any) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler {
        choices += {
            equals(arg1, asExpr(it))
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.table(
    arg1: Expr,
    arg2: Expr,
    tabler: (row: GoalScope.(Any, Any) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2 ->
        choices += {
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.table(
    arg1: Expr,
    arg2: Expr,
    arg3: Expr,
    tabler: (row: GoalScope.(Any, Any, Any) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2, g3 ->
        choices += {
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
            equals(arg3, asExpr(g3))
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.table(
    arg1: Expr,
    arg2: Expr,
    arg3: Expr,
    arg4: Expr,
    tabler: (row: GoalScope.(Any, Any, Any, Any) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2, g3, g4 ->
        choices += {
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
            equals(arg3, asExpr(g3))
            equals(arg4, asExpr(g4))
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.table(
    arg1: Expr,
    arg2: Expr,
    arg3: Expr,
    arg4: Expr,
    arg5: Expr,
    tabler: (row: GoalScope.(Any, Any, Any, Any, Any) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2, g3, g4, g5 ->
        choices += {
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
            equals(arg3, asExpr(g3))
            equals(arg4, asExpr(g4))
            equals(arg5, asExpr(g5))
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.match(
    arg1: Expr,
    tabler: (row: GoalScope.(Any, GoalScope.() -> Unit) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { arg, body ->
        choices += choice@{
            equals(arg1, asExpr(arg))
            if (currentScope.currentSolutions == SSNil) return@choice
            body()
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.match(
    arg1: Expr,
    arg2: Expr,
    tabler: (row: GoalScope.(Any, Any, GoalScope.() -> Unit) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2, body ->
        choices += choice@{
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
            if (currentScope.currentSolutions == SSNil) return@choice
            body()
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.match(
    arg1: Expr,
    arg2: Expr,
    arg3: Expr,
    tabler: (row: GoalScope.(Any, Any, Any, GoalScope.() -> Unit) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2, g3, body ->
        choices += choice@{
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
            equals(arg3, asExpr(g3))
            if (currentScope.currentSolutions == SSNil) return@choice
            body()
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.match(
    arg1: Expr,
    arg2: Expr,
    arg3: Expr,
    arg4: Expr,
    tabler: (row: GoalScope.(Any, Any, Any, Any, GoalScope.() -> Unit) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2, g3, g4, body ->
        choices += choice@{
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
            equals(arg3, asExpr(g3))
            equals(arg4, asExpr(g4))
            if (currentScope.currentSolutions == SSNil) return@choice
            body()
        }
    }
    choose(*choices.toTypedArray())
}

@GoalDSL
fun GoalScope.match(
    arg1: Expr,
    arg2: Expr,
    arg3: Expr,
    arg4: Expr,
    arg5: Expr,
    tabler: (row: GoalScope.(Any, Any, Any, Any, Any, GoalScope.() -> Unit) -> Unit) -> Unit
) {
    val choices: MutableList<GoalScope.() -> Unit> = mutableListOf()
    tabler { g1, g2, g3, g4, g5, body ->
        choices += choice@{
            equals(arg1, asExpr(g1))
            equals(arg2, asExpr(g2))
            equals(arg3, asExpr(g3))
            equals(arg4, asExpr(g4))
            equals(arg5, asExpr(g5))
            if (currentScope.currentSolutions == SSNil) return@choice
            body()
        }
    }
    choose(*choices.toTypedArray())
}

data class Solution(val system: System, val variables: Set<Var>) {
    override fun toString(): String {
        val renaming = mutableMapOf<Var, Var>()
        val cache = mutableMapOf<Expr, Expr>()
        return variables.joinToString("\n") {
            "$it = " + system.expand(it, renaming = renaming, cache = cache)
        }
    }

    operator fun iterator(): Iterator<Pair<Var, Expr>> = iterator {
        val renaming = mutableMapOf<Var, Var>()
        val cache = mutableMapOf<Expr, Expr>()
        for (v in variables) {
            yield(v to system.expand(v, renaming = renaming, cache = cache))
        }
    }
}

inline fun goal(
    scope: GoalScope = GoalScope(SSCons(System()), persistentHashMapOf(), stack()),
    crossinline body: GoalScope.() -> Unit
): SStream<Solution> =
    scope.apply(body).currentSolutions.map { Solution(it, scope.variables.values.toSet()) }

val nil = Constant("nil")
val cons by Function2

val natZero = Constant("zero")
val natBits by Function2
val zeroBit = Constant('0')
val oneBit = Constant('1')
val GoalScope.natOne: Expr get() = natBits['1', natZero]

val inc by ProjectedFunction1(
    forward = { require(it is Int); it + 1 },
    backward = { require(it is Int); it - 1 }
)

fun GoalScope.lst(vararg elements: Any) = elements.foldRight(nil as Expr) { e, l -> cons[e, l] }

fun GoalScope.nat(number: Int): Expr = when {
    number == 0 -> natZero
    number % 2 == 0 -> natBits[zeroBit, nat(number / 2)]
    else -> natBits[oneBit, nat((number - 1) / 2)]
}

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

val GoalScope.append: Expr.(Expr) -> Expr
    get() = { that ->
        any().also { out ->
            appendO(this, that, out)
        }
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

fun GoalScope.lengthO(l: Expr, out: Expr): Unit = scope {
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
            lengthO(t, tlen)
        }
    )
}

fun GoalScope.posO(n: Expr): Unit = scope {
    var n by bind(n)
    n = natBits[any(), any()]
}

fun GoalScope.aboveOneO(n: Expr): Unit = scope {
    var n by bind(n)
    n = natBits[any(), natBits[any(), any()]]
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

fun GoalScope.plusO(n: Expr, m: Expr, k: Expr) = adderO(zeroBit, n, m, k)

fun GoalScope.bitXorO(x: Expr, y: Expr, r: Expr) = table(x, y, r) { row ->
    row('0', '0', '0')
    row('0', '1', '1')
    row('1', '0', '1')
    row('1', '1', '0')
}

fun GoalScope.bitAndO(x: Expr, y: Expr, r: Expr) = table(x, y, r) { row ->
    row('0', '0', '0')
    row('1', '0', '0')
    row('0', '1', '0')
    row('1', '1', '1')
}

fun reify(expr: Expr): Any? = when(expr) {
    natZero -> 0
    is App -> when(expr.f) {
        natBits -> try {
            val base = reify(expr.args[1]) as Int
            val cc = if(expr.args[0] == oneBit) 1 else 0
            cc + base * 2
        } catch (e: Exception) { expr.toString() }
        else -> expr.toString()
    }
    else -> expr.toString()
}

fun main(args: Array<String>) {
    val res1 = goal {
        var n by vars
        var m by vars
        var r by vars
        //r = nat(13)
        plusO(n, m, r)

    }
    for (r in res1.take(50)) {
        println("---")
        for((k, v) in r) {
            println("$k = ${reify(v)}")
        }
    }
}

