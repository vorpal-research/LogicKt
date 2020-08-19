package ru.spbstu.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class LazyStreamTest {

    inline fun <T> assertEqualsForced(left: List<T>, right: SStream<T>) =
        assertEquals(left, right.toList())

    @Test
    fun smokeTest() {
        assertEqualsForced((0..15).toList(), (0..15).toSStream())
        assertEqualsForced(listOf(), listOf<Int>().toSStream())
        assertEqualsForced(listOf(), SSNil)
        assertEqualsForced(listOf(2), listOf(2).toSStream())
    }

    @Test
    fun toStringTest() {
        // toString is not forcing, but iteration is
        val s = (1..8).toSStream()
        assertEquals("[1, ...]", "$s")
        for(e in s.take(2)) {}
        assertEquals("[1, 2, ...]", "$s")
        for(e in s.take(3)) {}
        assertEquals("[1, 2, 3, ...]", "$s")
        for(e in s) {}
        assertEquals("[1, 2, 3, 4, 5, 6, 7, 8]", "$s")
    }

    @Test
    fun mapTest() {
        assertEqualsForced((2..17).toList(), (0..15).toSStream().map { it + 2 })
        assertEqualsForced(listOf(2), (0..0).toSStream().map { it + 2 })
        assertEqualsForced(listOf(), listOf<Int>().toSStream().map { it + 2 })
    }

    @Test
    fun mixTest() {
        assertEqualsForced(
            listOf(),
            SSNil mix { SSNil }
        )
        assertEqualsForced(
            (1..4).toList(),
            ((1..4).toSStream() mix { SSNil })
        )
        assertEqualsForced(
            (1..4).toList(),
            SSNil mix { (1..4).toSStream() }
        )
        assertEqualsForced(
            "1a2b3c4defg".toList(),
            (('1'..'4').toSStream() mix { ('a'..'g').toSStream() })
        )
        assertEqualsForced(
            "a1b2c3d4efg".toList(),
            (('a'..'g').toSStream() mix { ('1'..'4').toSStream() })
        )
    }

    @Test
    fun mapNotNullTest() {
        fun <T, U: Any> assertBehavesLikeList(lst: Iterable<T>, body: (T) -> U?) =
            assertEqualsForced(lst.mapNotNull(body), lst.toSStream().mapNotNull(body))

        assertBehavesLikeList((2..17)) { it + 2 }
        assertBehavesLikeList(listOf(2)) { it + 2 }
        assertBehavesLikeList(listOf<Int>()) { it + 2 }

        assertBehavesLikeList((0..15)) { it.takeIf { it % 2 == 0 } }
        assertBehavesLikeList((0..0)) { it.takeIf { it % 2 == 0 } }
        assertBehavesLikeList(listOf(3)) { it.takeIf { it % 2 == 0 } }
        assertBehavesLikeList(listOf<Int>()) { it.takeIf { it % 2 == 0 } }

        assertBehavesLikeList((0..40)) { it.takeIf { it !in 20..30 } }
    }

    @Test
    fun mixMapTest() {
        fun <T, U: Any> assertBehavesLikeFlatMap(lst: Iterable<T>, body: (T) -> SStream<U>) =
            assertEquals(lst.asSequence().flatMapTo(mutableSetOf(), body),
                lst.toSStream().mixMap(body).toSet())

        assertBehavesLikeFlatMap(listOf<Int>()) { (0..it).toSStream() }
        assertBehavesLikeFlatMap(listOf<Int>()) { SSNil }
        assertBehavesLikeFlatMap(0..10) { (0..it).toSStream() }
        assertBehavesLikeFlatMap(0..10) { SSNil }
        assertBehavesLikeFlatMap(0..100) { it.takeIf { it !in 20..80 }?.let { (0..it).toSStream() } ?: SSNil }
        assertBehavesLikeFlatMap(0..100) { it.takeIf { it !in 20..80 }?.let { (-it..0).toSStream() } ?: SSNil }
        assertBehavesLikeFlatMap(0..100) { it.takeIf { it % 3 == 0 }?.let { (0..it).toSStream() } ?: SSNil }
        assertBehavesLikeFlatMap(0..100) { it.takeIf { it % 3 == 0 }?.let { (-it..0).toSStream() } ?: SSNil }
    }

}
