package ru.spbstu.logic

infix fun <T> List<T>.identityEquals(that: List<T>): Boolean {
    if(size != that.size) return false
    for(i in 0..lastIndex) {
        if(this[i] !== that[i]) return false
    }
    return true
}

abstract class Source<out T>() {
    abstract fun generate(): T

    val storage: MutableList<@UnsafeVariance T> = mutableListOf()

    fun ensureIndex(ix: Int): T {
        while(storage.size <= ix) storage.add(generate())
        return storage[ix]
    }

    operator fun component1(): T = ensureIndex(0)
    operator fun component2(): T = ensureIndex(1)
    operator fun component3(): T = ensureIndex(2)
    operator fun component4(): T = ensureIndex(3)
    operator fun component5(): T = ensureIndex(4)
    operator fun component6(): T = ensureIndex(5)
    operator fun component7(): T = ensureIndex(6)
    operator fun component8(): T = ensureIndex(7)
    operator fun component9(): T = ensureIndex(8)
    operator fun component10(): T = ensureIndex(9)
    operator fun component11(): T = ensureIndex(10)
    operator fun component12(): T = ensureIndex(11)
    operator fun component13(): T = ensureIndex(12)
    operator fun component14(): T = ensureIndex(13)
    operator fun component15(): T = ensureIndex(14)
}

inline fun <T> Source(crossinline body: () -> T): Source<T> = object: Source<T>() {
    override fun generate(): T = body()
}
