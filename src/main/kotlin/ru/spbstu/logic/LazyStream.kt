package ru.spbstu.logic

sealed class SStream<out T> : Sequence<T>
object SSNil : SStream<Nothing>() {
    object NilIterator : Iterator<Nothing> {
        override fun hasNext(): Boolean = false
        override fun next(): Nothing = throw NoSuchElementException()
    }

    override fun iterator(): Iterator<Nothing> = NilIterator

    override fun toString(): String = "[]"
}

data class SSCons<out T>(val head: T, val lazyTail: Lazy<SStream<T>>? = null) : SStream<T>() {
    constructor(head: T, fTail: () -> SStream<T>) : this(head, lazy(fTail))

    val tail get() = lazyTail?.value ?: SSNil
    override fun iterator(): Iterator<T> = iterator {
        var current: SStream<T> = this@SSCons
        loop@ while (true) {
            when (current) {
                is SSCons -> {
                    yield(current.head)
                    current = current.tail
                }
                is SSNil -> break@loop
            }
        }
    }

    override fun toString(): String = buildString {
        append('[')
        var current: SStream<T> = this@SSCons
        while (current is SSCons) {
            append(current.head)
            val lazyTail = current.lazyTail ?: break
            if (!lazyTail.isInitialized()) {
                append(", ...")
                break
            }
            current = lazyTail.value
            if(current is SSCons) append(", ")
        }

        append(']')
    }
}

infix fun <T> SStream<T>.mix(that: () -> SStream<T>): SStream<T> = when (this) {
    is SSNil -> that()
    is SSCons -> {
        SSCons(head) { that().mix { tail } }
    }
}

fun <T, U> SStream<T>.map(body: (T) -> U): SStream<U> = when (this) {
    is SSNil -> SSNil
    is SSCons -> SSCons(body(head)) {
        tail.map(body)
    }
}

fun <T, U: Any> SStream<T>.mapNotNull(body: (T) -> U?): SStream<U> = when (this) {
    is SSNil -> SSNil
    is SSCons -> run {
        var current: SStream<T> = this
        while (current is SSCons) {
            val bhead = body(current.head)
            if (bhead != null) {
                val ctail = current.tail
                return@run SSCons(bhead) { ctail.mapNotNull(body) }
            }
            current = current.tail
        }
        SSNil
    }
}

fun <T, U> SStream<T>.mixMap(body: (T) -> SStream<U>): SStream<U> = run {
    when (this) {
        is SSNil -> SSNil
        is SSCons -> /* oh, it looked so much better recursion-style =( */
            when (val bhead = body(head)) {
                !is SSNil -> bhead mix { tail.mixMap(body) }
                else -> {
                    var current = tail
                    while (current is SSCons) {
                        val bhead = body(current.head)
                        if (bhead !is SSNil) {
                            val current = current
                            return@run bhead mix { current.tail.mixMap(body) }
                        }
                        current = current.tail
                    }
                    SSNil
                }
            }
    }
}

fun <T> Iterator<T>.toSStream(): SStream<T> = when {
    !hasNext() -> SSNil
    else -> SSCons(next()) { toSStream() }
}

fun <T> Iterable<T>.toSStream(): SStream<T> = iterator().toSStream()
fun <T> Sequence<T>.toSStream(): SStream<T> = iterator().toSStream()
