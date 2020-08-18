package ru.spbstu.logic

import ru.spbstu.wheels.hashCombine
import kotlin.reflect.KProperty

sealed class Expr {
    abstract fun <C : MutableCollection<Var>> vars(collector: C): C
    fun vars(): Set<Var> = vars(mutableSetOf())

    abstract fun subst(map: Map<Var, Expr>): Expr
}

object Invalid: Expr() {
    override fun <C : MutableCollection<Var>> vars(collector: C): C = collector
    override fun subst(map: Map<Var, Expr>): Expr = this
    override fun toString(): String = "<INVALID>"
}

sealed class Atom : Expr()
sealed class Var : Atom() {
    abstract val name: String

    override fun <C : MutableCollection<Var>> vars(collector: C): C = collector.apply { add(this@Var) }
    override fun subst(map: Map<Var, Expr>) = map[this] ?: this
    override fun toString(): String = name
}

data class ScopeVar(override val name: String, val scopeId: Int) : Var() {
    override fun toString(): String = super.toString()
}

class BoundVar(id: Int) : Var() {
    override val name: String = "α$id"
}

class TempVar : Var() {
    companion object {
        var counter = 0
        fun freshId() = ++counter
    }

    override val name: String = "β${freshId()}"
}

data class Constant<T>(val value: T) : Atom() {
    override fun <C : MutableCollection<Var>> vars(collector: C): C = collector

    override fun subst(map: Map<Var, Expr>) = this

    override fun toString(): String = "$value"

    companion object {
        fun Symbol(name: String): Constant<Symbol> = Symbol.get(name)
    }
}

data class MuExpr(val base: Var, val body: Expr) : Expr() {
    override fun <C : MutableCollection<Var>> vars(collector: C): C {
        val cc = body.vars(collector)
        cc.remove(base)
        return cc
    }

    override fun subst(map: Map<Var, Expr>): Expr = copy(body = body.subst(map))

    override fun toString(): String = "μ $base -> $body"
}

fun MuExpr(body: (Expr) -> Expr): MuExpr {
    val v = TempVar()
    return MuExpr(v, body(v))
}

sealed class Function {
    abstract val name: String
    override fun toString(): String = name
}

data class Function1(override val name: String) : Function() {
    companion object {
        operator fun getValue(self: Any?, prop: KProperty<*>): Function1 =
            Function1(prop.name)
    }

    override fun toString(): String = super.toString()
}

data class Function2(override val name: String) : Function() {
    companion object {
        operator fun getValue(self: Any?, prop: KProperty<*>): Function2 =
            Function2(prop.name)
    }

    override fun toString(): String = super.toString()
}

data class Function3(override val name: String) : Function() {
    companion object {
        operator fun getValue(self: Any?, prop: KProperty<*>): Function3 =
            Function3(prop.name)
    }

    override fun toString(): String = super.toString()
}

data class Function4(override val name: String) : Function() {
    companion object {
        operator fun getValue(self: Any?, prop: KProperty<*>): Function4 =
            Function4(prop.name)
    }

    override fun toString(): String = super.toString()
}

sealed class AppBase() : Expr() {
    abstract val args: List<Expr>
    abstract fun copy(args: List<Expr> = this.args): Expr?
}

data class App(val f: Function, override val args: List<Expr>) : AppBase() {
    constructor(f: Function, vararg args: Expr) : this(f, args.asList())

    override fun copy(args: List<Expr>): Expr = copy(f = f, args = args)

    override fun <C : MutableCollection<Var>> vars(collector: C): C {
        for (arg in args) arg.vars(collector)
        return collector
    }

    override fun subst(map: Map<Var, Expr>) = copy(args = args.map { it.subst(map) })

    override fun toString(): String = args.joinToString(prefix = "$f[", postfix = "]")

    val hash = hashCombine(f, args)
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = other is App
            && hash == other.hash
            && f == other.f
            && args == other.args
}

interface ProjectedFunction1 {
    operator fun invoke(arg: Any?): Any?
    fun uninvoke(result: Any?): Any?
}

inline fun ProjectedFunction1(
    name: String = "<anonymous>",
    crossinline forward: (Any?) -> Any?,
    crossinline backward: (Any?) -> Any?
) = object : ProjectedFunction1 {
    override fun invoke(arg: Any?): Any? = forward(arg)
    override fun uninvoke(result: Any?): Any? = backward(result)
    override fun toString(): String = name
}

inline operator fun ProjectedFunction1.getValue(self: Any?, prop: KProperty<*>) = let { self ->
    object : ProjectedFunction1 {
        override fun invoke(arg: Any?): Any? = self(arg)
        override fun uninvoke(result: Any?): Any? = self.uninvoke(result)
        override fun toString(): String = prop.name
    }
}

inline val ProjectedFunction1.inverse
    get() = let { self ->
        object : ProjectedFunction1 {
            override fun invoke(arg: Any?): Any? = self.uninvoke(arg)
            override fun uninvoke(result: Any?): Any? = self.invoke(result)
            override fun toString(): String = "$self.inverse"
        }
    }

operator fun ProjectedFunction1.plus(rhv: ProjectedFunction1): ProjectedFunction1 = let { lhv ->
    object : ProjectedFunction1 {
        override fun invoke(arg: Any?): Any? = lhv.invoke(rhv.invoke(arg))
        override fun uninvoke(result: Any?): Any? = rhv.uninvoke(lhv.uninvoke(result))
        override fun toString(): String = "($lhv + $rhv)"
    }
}

fun ProjectedApp(f: ProjectedFunction1, arg: Expr): Expr {
    return when (arg) {
        is Var -> ProjectedApp(f = f, v = arg)
        is Constant<*> -> try {
            Constant(f.invoke(arg.value))
        } catch (ex: IllegalArgumentException) {
            Invalid
        }
        is ProjectedApp -> ProjectedApp(
            f + arg.f,
            arg.v
        )
        else -> Invalid
    }
}

data class ProjectedApp constructor(val f: ProjectedFunction1, val v: Var) : AppBase() {
    override val args: List<Expr>
        get() = listOf(v)

    override fun copy(args: List<Expr>): Expr? {
        val arg = args.single()
        return ProjectedApp(f = f, arg = arg)
    }

    override fun <C : MutableCollection<Var>> vars(collector: C): C {
        v.vars(collector)
        return collector
    }

    override fun subst(map: Map<Var, Expr>) = ProjectedApp(
        f = f,
        arg = v.subst(map)
    ) ?: Constant(null)

    override fun toString(): String = "$f[$v]"
}

class Symbol private constructor(val name: String) {
    override fun equals(other: Any?): Boolean = other is Symbol && name == other.name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    companion object {
        fun get(name: String): Constant<Symbol> = Constant(Symbol(name))
        operator fun getValue(thisRef: Any?, prop: KProperty<*>): Expr = get(prop.name)
    }
}
