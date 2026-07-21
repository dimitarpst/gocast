package app.gocast.domain

import kotlin.random.Random

/** Unambiguous 4-character pairing code (no 0/O/1/I). */
object ShortCodeGenerator {
    const val LENGTH = 4
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun generate(length: Int = LENGTH, random: Random = Random.Default): String =
        buildString(length) {
            repeat(length) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
}
