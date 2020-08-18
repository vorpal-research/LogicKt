package ru.spbstu.logic.lib;

import ru.spbstu.logic.ProjectedFunction1
import ru.spbstu.logic.getValue

val inc by ProjectedFunction1(
    forward = { require(it is Int); it + 1 },
    backward = { require(it is Int); it - 1 }
)
