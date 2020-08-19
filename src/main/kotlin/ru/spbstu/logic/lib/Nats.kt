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

/*
(define mulo
       (\ (n m p)
       (matche ((n m))
               ((((()) _))  (== () p))
               (((_ (())))  (== () p) (poso n))
               (((((1)) _)) (== m p) (poso m))
               (((_ ((1)))) (== n p) (>1o n))
               (((((0 . x)) _))
                         (exist (z)
                                (== ((0 . z)) p)
                                (poso x)
                                (poso z)
                                (>1o m)
                                (mulo x m z)
                        )
                )
               (((((1 . x)) ((0 . y)))) (poso x) (poso y)(mulo m n p))
               (((((1 . x)) ((1 . y)))) (poso x) (poso y)(odd-mulo x n m p))
        ))
)
*/
fun GoalScope.mulO(n: Expr, m: Expr, p: Expr): Unit {
    scope {
        var n by bind(n)
        var m by bind(m)
        var p by bind(p)
        match(n, m) { case ->
            case(natZero, any()) { p = natZero }
            case(any(), natZero) { p = natZero; posO(n) }
            case(natOne, any())  { p = m; posO(m) }
            case(any(), natOne)  { p = n; aboveOneO(n) }

            val x by vars
            case(natBits[zeroBit, x], any()) {
                var z by vars
                p = natBits[zeroBit, z]
                posO(x)
                posO(z)
                aboveOneO(m)
                mulO(x, m, z)
            }

            val y by vars
            case(natBits[oneBit, x], natBits[zeroBit, y]) {
                posO(x)
                posO(y)
                mulO(m, n, p)
            }
            case(natBits[oneBit, x], natBits[oneBit, y]) {
                posO(x)
                posO(y)
                oddMulO(x, n, m, p)
            }
        }
    }
}

/*
(define odd-mulo
        (\ (x n m p)
        ( exist(q)
             (bound-mulo q p n m)
             (mulo x m q)
             (pluso ((0 . q)) m p)
        ))
)
*/
fun GoalScope.oddMulO(x: Expr, n: Expr, m: Expr, p: Expr) {
    scope {
        val x by bind(x)
        val n by bind(n)
        val m by bind(m)
        val p by bind(p)
        var q by vars

        boundMulO(q, p, n, m)
        mulO(x, m, q)
        plusO(natBits[zeroBit, q], m, p)
    }
}
/*
(define bound-mulo
        (\ (q p n m)
        (matche ((q p))
                ((((()) ((_ . _)))))
                (((((_ . x)) ((_ . y))))
                        (exist(a z)
                              (conde ((== (())n) (== ((a . z))m) (bound-mulo x y z (())))
                                     ((== ((a . z))n) (bound-mulo x y z m))
                              )
                        )
                )
        ))
)
* */
fun GoalScope.boundMulO(q: Expr, p: Expr, n: Expr, m: Expr) {
    scope {
        val q by bind(q)
        val p by bind(p)
        var n by bind(n)
        var m by bind(m)

        match(q, p) { case ->
            case(natZero, natBits[any(), any()]) {}
            val x by vars
            val y by vars
            case(natBits[any(), x], natBits[any(), y]) {
                val a by vars
                val z by vars
                choose(
                    {
                        n = natZero
                        m = natBits[a, z]
                        boundMulO(x, y, z, natZero)
                    },
                    {
                        n = natBits[a, z]
                        boundMulO(x, y, z, m)
                    }
                )
            }
        }
    }
}

/*
(define =lo
        (\e (n m)
            ((((()) (()))))
            (((((1)) ((1)))))
            (((((a . x)) ((b . y))))
                (poso x)
                (poso y)
                (=lo x y)
            )
        )
)
* */
fun GoalScope.eqlO(n: Expr, m: Expr) {
    scope {
        val n by bind(n)
        val m by bind(m)
        match(n, m) { case ->
            case(natZero, natZero) {}
            case(natOne, natOne) {}

            val x by vars
            val y by vars
            case(natBits[any(), x], natBits[any(), y]) {
                posO(x)
                posO(y)
                eqlO(x, y)
            }
        }
    }
}

/*
(define <lo
       (\e (n m)
           ((((()) _))
                (poso m))
           (((((1)) _))
                (>1o m))
           (((((a . x)) ((b . y))))
                (poso x) (poso y) (<lo x y))
       )
)
*/
fun GoalScope.lesslO(n: Expr, m: Expr) {
    scope {
        val n by bind(n)
        val m by bind(m)
        val x by vars
        val y by vars
        match(n, m) { case ->
            case(natZero, any()) {
                posO(m)
            }
            case(natOne, any()) {
                aboveOneO(m)
            }
            case(natBits[any(), x], natBits[any(), y]) {
                posO(x)
                posO(y)
                lesslO(x, y)
            }
        }
    }
}

/*
(define <=lo
	(\ (n m)
		(conde ((=lo n m)) ((<lo n m)))
	)
)
*/
fun GoalScope.lessEqlO(n: Expr, m: Expr) {
    scope {
        val n by n
        val m by m
        choose(
            { eqlO(n, m) }, { lesslO(n, m) }
        )
    }
}

/*
(define <o
	(\ (n m)
		(conde
			((<lo n m))
			((=lo n m) (exist(x)
					(poso x)
					(pluso n x m)
				   )
			)
		)
	)
)
* */
fun GoalScope.lessO(n: Expr, m: Expr) {
    scope {
        val n by bind(n)
        val m by bind(m)

        choose(
            { lesslO(n, m) },
            {
                eqlO(n, m)
                val x by vars
                posO(x)
                plusO(n, x, m)
            }
        )
    }
}

/*
(define <=o
	(\ (n m)
		(conde ((== n m)) ((<o n m)))
	)
)
*/
fun GoalScope.lessEqO(n: Expr, m: Expr) {
    scope {
        var n by bind(n)
        val m by bind(m)
        choose(
            { n = m },
            { lessO(n, m) }
        )
    }
}

/*
(define divo
        (\ (n m q r)
                (matche q
                        ((())
                                (== r n) (<o n m))
                        (((1))
                                (=lo n m) (pluso r m n) (<o r m))
                        (_
                                (<lo m n)
                                (<o r m)
                                (poso q)
                                (exist (nh nl qh ql qlm qlmr rr rh)
                                        (splito n r nl nh)
                                        (splito q r ql qh)
                                        (conde
                                                (
                                                        (== (()) nh)
                                                        (== (()) qh)
                                                        (minuso nl r qlm)
                                                        (mulo ql m qlm)
                                                )
                                                (
                                                        (poso nh)
                                                        (mulo ql m qlm)
                                                        (pluso qlm r qlmr)
                                                        (minuso qlmr nl rr)
                                                        (splito rr r (()) rh)
                                                        (divo nh m qh rh)
                                                )
                                        )
                                )
                        )
                )))
* */
fun GoalScope.divO(n: Expr, m: Expr, q: Expr, r: Expr) {
    scope {
        val n by n
        val m by m
        var q by q
        var r by r
        match(q) { case ->
            case(natZero) {
                r = n
                lessO(n, m)
            }
            case(natOne) {
                eqlO(n, m)
                plusO(r, m, n)
                lessO(r, m)
            }
            case(any()) {
                lesslO(m, n)
                lessO(r, m)
                posO(q)

                exists { (nh, nl, qh, ql, qlm, qlmr, rr, rh) ->
                    splitO(n, r, nl, nh)
                    splitO(q, r, ql, qh)
                    choose(
                        {
                            var nh by nh
                            var qh by qh
                            nh = natZero
                            qh = natZero
                            minusO(nl, q, qlm)
                            mulO(ql, m, qlm)
                        },
                        {
                            posO(nh)
                            mulO(ql, m, qlm)
                            plusO(qlm, r, qlmr)
                            minusO(qlmr, nl, rr)
                            splitO(rr, r, natZero, rh)
                            divO(nh, m, qh, rh)
                        }
                    )
                }
            }
        }
    }
}

/*
(define splito
        (\e (n r l h)
            ((((()) _ (()) (()))))
            (((((0 b . xn)) (()) (()) ((b . xn)))))
            (((((1 . xn)) (()) ((1)) xn)))
            (((((0 b . xn)) ((a . xr)) (()) _))
                (splito ((b . xn)) xr (()) h))
            (((((1 . xn)) ((a . xr)) ((1)) _))
                (splito xn xr (()) h))
            (((((b . xn)) ((a . xr)) ((b . xl)) _))
                (poso xl)
                (splito xn xr xl h))
        )
)
* */

fun GoalScope.splitO(n: Expr, r: Expr, l: Expr, h: Expr) {
    scope {
        val n by n
        val r by r
        val l by l
        val h by h
        match(n, r, l, h) { case ->
            val a by vars
            val b by vars
            val xn by vars
            val xr by vars
            val xl by vars

            case(natZero, any(), natZero, natZero) {}
            case(natBits[zeroBit, natBits[b, xn]], natZero, natZero, natBits[b, xn]) {}
            case(natBits[oneBit, xn], natZero, natOne, xn) {}
            case(natBits[zeroBit, natBits[b, xn]], natBits[a, xr], natZero, any()) {
                splitO(natBits[b, xn], xr, natZero, h)
            }
            case(natBits[oneBit, xn], natBits[a, xr], natOne, any()) {
                splitO(xn, xr, natZero, h)
            }
            case(natBits[b, xn], natBits[a, xr], natBits[b, xl], any()) {
                posO(xl)
                splitO(xn, xr, xl, h)
            }
        }
    }
}
