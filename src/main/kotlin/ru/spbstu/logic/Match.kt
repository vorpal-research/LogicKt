package ru.spbstu.logic

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
            if (currentSolutions == SSNil) return@choice
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
            if (currentSolutions == SSNil) return@choice
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
            if (currentSolutions == SSNil) return@choice
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
            if (currentSolutions == SSNil) return@choice
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
            if (currentSolutions == SSNil) return@choice
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
