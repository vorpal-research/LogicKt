package ru.spbstu.logic

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf

class PersistentDisjointSet<T>(
    private val links: PersistentMap.Builder<T, T> = persistentHashMapOf<T, T>().builder(),
    private val sizes: PersistentMap.Builder<T, Int> = persistentHashMapOf<T, Int>().builder()
) {
    private var T.parent: T
        get() = links.getOrPut(this) { this }
        set(value) { links[this] = value }
    var T.size: Int
        get() = sizes.getOrPut(this) { 1 }
        private set(value) { sizes[this] = value }

    val T.root: T get() {
        val p = parent
        if(p != this) {
            val proot = p.root
            parent = proot
            return proot
        }
        return p
    }

    fun find(value: T): T = value.root

    fun union(lhv: T, rhv: T): PersistentDisjointSet<T> {
        val lhvr = lhv.root
        val rhvr = rhv.root
        if(lhvr == rhvr) return this

        return copy().apply {
            if(lhvr.size > rhvr.size) {
                rhvr.parent = lhvr
                lhvr.size += rhvr.size
            } else {
                lhvr.parent = rhvr
                rhvr.size += lhvr.size
            }
        }
    }

    fun unionForced(lhv: T, rhv: T): PersistentDisjointSet<T> {
        val lhvr = lhv.root
        val rhvr = rhv.root
        if(lhvr == rhvr) return this

        return copy().apply {
            lhvr.parent = rhvr
            rhvr.size += lhvr.size
        }
    }

    fun copy(): PersistentDisjointSet<T> =
        PersistentDisjointSet(links.build().builder(), sizes.build().builder())
}