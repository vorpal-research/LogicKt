package ru.spbstu.logic

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import ru.spbstu.wheels.Stack
import ru.spbstu.wheels.memoize
import ru.spbstu.wheels.stack
import kotlin.reflect.KProperty


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

    val currentScope get() = scopeStack.top ?: this

    fun equals(lhv: Expr, rhv: Expr) {
        currentSolutions = currentSolutions.mapNotNull { it.equals(lhv, rhv) }
    }

    fun never() {
        currentScope.currentSolutions = SSNil
    }

    inline fun <T> fork(solutions: SStream<System>, crossinline body: GoalScope.() -> T): SStream<System> {
        if (solutions is SSNil) return SSNil
        val freshScope = GoalScope(solutions, variables, scopeStack)
        scopeStack.push(freshScope)
        freshScope.body()
        scopeStack.pop()
        return freshScope.currentSolutions
    }

    @GoalDSL
    inline fun scope(crossinline body: GoalScope.() -> Unit) {
        currentSolutions = fork(currentSolutions, body)
    }

    @GoalDSL
    fun exists(body: GoalScope.(Source<Expr>) -> Unit) {
        scope {
            var counter = 0
            val varStream = Source { bindVariable("\$bound${++counter}") }
            body(varStream)
        }
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
        TODO()
    }

    class VarsDelegate(val variable: Var)
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
        return VarsDelegate(v)
    }

    operator fun VarsDelegate.getValue(self: Any?, prop: KProperty<*>): Expr = variable

    operator fun VarsDelegate.setValue(self: Any?, prop: KProperty<*>, rhv: Expr) {
        currentScope.equals(variable, rhv)
    }

    fun bind(e: Expr): BindingDelegate = BindingDelegate(e)
    operator fun Expr.provideDelegate(thisRef: Any?, prop: KProperty<*>) =
        bind(this).provideDelegate(thisRef, prop)

    fun any(): Expr = TempVar()

    operator fun Function1.get(arg1: Expr) =
        App(this, arg1)

    operator fun Function2.get(arg1: Expr, arg2: Expr) =
        App(this, arg1, arg2)

    operator fun Function3.get(arg1: Expr, arg2: Expr, arg3: Expr) =
        App(this, arg1, arg2, arg3)

    operator fun Function4.get(arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr) =
        App(this, arg1, arg2, arg3, arg4)

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

    fun asExpr(value: Any) = when (value) {
        is Expr -> value
        else -> Constant(value)
    }

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
        ProjectedApp(this, asExpr(arg))
}

@GoalDSL
inline fun goal(
    scope: GoalScope = GoalScope(SSCons(System()), persistentMapOf(), stack()),
    crossinline body: GoalScope.() -> Unit
): SStream<Solution> =
    scope.apply(body).currentSolutions.map {
        Solution(
            it,
            scope.variables.values.toSet()
        )
    }
