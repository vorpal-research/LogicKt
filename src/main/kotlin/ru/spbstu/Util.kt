package ru.spbstu

infix fun <T> List<T>.identityEquals(that: List<T>): Boolean {
    if(size != that.size) return false
    for(i in 0..lastIndex) {
        if(this[i] !== that[i]) return false
    }
    return true
}
