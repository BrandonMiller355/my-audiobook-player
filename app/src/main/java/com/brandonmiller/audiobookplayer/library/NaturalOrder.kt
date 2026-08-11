package com.brandonmiller.audiobookplayer.library

/**
 * Orders names the way a person reads them: runs of digits compare numerically, everything
 * else compares case-insensitively.
 *
 * PRD §17's failure case is `Chapter 1, Chapter 10, Chapter 2`. Real books in the library also
 * need disc-track names (`1-01 … 1-10 … 2-01 … 9-15`) and embedded numbers (`01 of 09`) to come
 * out right.
 */
object NaturalOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0

        while (i < a.length && j < b.length) {
            val left = a[i]
            val right = b[j]

            if (left.isDigit() && right.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++

                // Compared as digit strings with leading zeros stripped, so arbitrarily long
                // runs work without overflowing a numeric type.
                val digitsA = a.substring(i, endA).trimStart('0')
                val digitsB = b.substring(j, endB).trimStart('0')

                if (digitsA.length != digitsB.length) return digitsA.length - digitsB.length
                val digits = digitsA.compareTo(digitsB)
                if (digits != 0) return digits

                i = endA
                j = endB
            } else {
                val chars = left.lowercaseChar().compareTo(right.lowercaseChar())
                if (chars != 0) return chars
                i++
                j++
            }
        }

        // Whichever still has characters left sorts after.
        return (a.length - i) - (b.length - j)
    }
}
