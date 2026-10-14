package com.vamshi.field.ui.util

/** "1 athlete", "3 athletes". Counts were interpolated straight in and read "1 athletes". */
fun countOf(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"
