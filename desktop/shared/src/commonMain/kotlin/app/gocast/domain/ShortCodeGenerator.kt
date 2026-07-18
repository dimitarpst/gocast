package app.gocast.domain

import kotlin.random.Random

/** Unambiguous 4-character pairing code (no 0/O/1/I). */
object ShortCodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun generate(length: Int = 4, random: Random = Random.Default): String =
        buildString(length) {
            repeat(length) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
}
