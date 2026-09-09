package com.mateusrodcosta.apps.lontramusic.utils

import java.util.Calendar
import kotlin.random.Random

private val threadLocalRandom = ThreadLocal.withInitial {
    Random(Calendar.getInstance().timeInMillis)
}

/** https://issuetracker.google.com/issues/234631055 just in case */
val Random
    get() = threadLocalRandom.get()!!
