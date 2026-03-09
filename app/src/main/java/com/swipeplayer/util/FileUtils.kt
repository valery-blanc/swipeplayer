package com.swipeplayer.util

import com.swipeplayer.data.VideoFile

/**
 * Natural (case-insensitive, numeric-aware) comparator for filenames.
 *
 * Tokenises each name into alternating numeric / non-numeric segments,
 * then compares segment by segment:
 *   - Two numeric segments → compared as Long values (2 < 10)
 *   - Otherwise           → compared lexicographically, ignoring case
 *
 * Examples:
 *   "video2.mp4"  < "video10.mp4"   (numeric: 2 < 10)
 *   "Episode 9"   < "Episode 10"    (numeric: 9 < 10)
 *   "abc.mp4"     < "ABC2.mp4"      (case-insensitive: "abc" == "abc", then "" < "2")
 */
private val TOKEN_REGEX = Regex("""(\d+)|(\D+)""")

fun naturalCompare(a: String, b: String): Int {
    val tokensA = TOKEN_REGEX.findAll(a.lowercase()).map { it.value }.toList()
    val tokensB = TOKEN_REGEX.findAll(b.lowercase()).map { it.value }.toList()

    val len = minOf(tokensA.size, tokensB.size)
    for (i in 0 until len) {
        val ta = tokensA[i]
        val tb = tokensB[i]
        val na = ta.toLongOrNull()
        val nb = tb.toLongOrNull()
        val cmp = when {
            na != null && nb != null -> na.compareTo(nb)
            else -> ta.compareTo(tb)
        }
        if (cmp != 0) return cmp
    }
    return tokensA.size.compareTo(tokensB.size)
}

/** Sorts a list of [VideoFile] by filename using [naturalCompare]. */
fun List<VideoFile>.sortedNaturally(): List<VideoFile> =
    sortedWith { a, b -> naturalCompare(a.name, b.name) }
