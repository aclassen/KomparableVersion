package org.burnoutcrew.komparableversion
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * <p>
 * Generic implementation of version comparison.
 * </p>
 *
 * Features:
 * <ul>
 * <li>mixing of '<code>-</code>' (hyphen) and '<code>.</code>' (dot) separators,</li>
 * <li>transition between characters and digits also constitutes a separator:
 *     <code>1.0alpha1 =&gt; [1, 0, alpha, 1]</code></li>
 * <li>unlimited number of version components,</li>
 * <li>version components in the text can be digits or strings,</li>
 * <li>strings are checked for well-known qualifiers and the qualifier ordering is used for version ordering.
 *     Well-known qualifiers (case insensitive) are:<ul>
 *     <li><code>alpha</code> or <code>a</code></li>
 *     <li><code>beta</code> or <code>b</code></li>
 *     <li><code>milestone</code> or <code>m</code></li>
 *     <li><code>rc</code> or <code>cr</code></li>
 *     <li><code>snapshot</code></li>
 *     <li><code>(the empty string)</code> or <code>ga</code> or <code>final</code></li>
 *     <li><code>sp</code></li>
 *     </ul>
 *     Unknown qualifiers are considered after known qualifiers, with lexical order (always case insensitive),
 *   </li>
 * <li>a hyphen usually precedes a qualifier, and is always less important than something preceded with a dot.</li>
 * </ul>
 *
 * @see <a href="https://cwiki.apache.org/confluence/display/MAVENOLD/Versioning">"Versioning" on Maven Wiki</a>
 * @author <a href="mailto:kenney@apache.org">Kenney Westerhof</a>
 * @author <a href="mailto:hboutemy@apache.org">Hervé Boutemy</a>
 *
 * Kotlin conversion
 * @author <a href="mailto:aclassen@hotmail.de">André Claßen</a>
 */

class KomparableVersion(version: String) : Comparable<KomparableVersion> {
    private var value: String = ""
    private var items = Item.ListItem()
    private var _canonical: String? = null
    val canonical: String
        get() = _canonical ?: items.toString().apply { _canonical = this }

    init {
        parseVersion(version)
    }

    fun parseVersion(version: String) {
        value = version
        val ver = version.lowercase()
        var list = Item.ListItem().apply { items = this }
        val stack = ArrayDeque<Item>().apply { addFirst(list) }
        var isDigit = false
        var startIndex = 0
        for (i in ver.indices) {
            val c = ver[i]
            when {
                c == '.' -> {
                    if (i == startIndex) {
                        list.add(Item.IntItem.ZERO)
                    } else {
                        list.add(parseItem(isDigit, ver.substring(startIndex, i)))
                    }
                    startIndex = i + 1
                }
                c == '-' -> {
                    if (i == startIndex) {
                        list.add(Item.IntItem.ZERO)
                    } else {
                        list.add(parseItem(isDigit, ver.substring(startIndex, i)))
                    }
                    startIndex = i + 1
                    list.add(Item.ListItem().also { list = it })
                    stack.addFirst(list)
                }
                c.isDigit() -> {
                    if (!isDigit && i > startIndex) {
                        list.add(Item.StringItem(ver.substring(startIndex, i), true))
                        startIndex = i
                        list.add(Item.ListItem().apply { list = this })
                        stack.addFirst(list)
                    }
                    isDigit = true
                }
                else -> {
                    if (isDigit && i > startIndex) {
                        list.add(parseItem(true, ver.substring(startIndex, i)))
                        startIndex = i
                        list.add(Item.ListItem().apply { list = this })
                        stack.addFirst(list)
                    }
                    isDigit = false
                }
            }
        }
        if (ver.length > startIndex) {
            list.add(parseItem(isDigit, ver.substring(startIndex)))
        }
        while (stack.isNotEmpty()) {
            list = stack.removeFirst() as Item.ListItem
            list.normalize()
        }
        _canonical = null
    }

    private sealed class Item : Comparable<Item?> {
        abstract val isNull: Boolean

        /**
         * Represents a numeric item in the version item list that can be represented with an int.
         */
        class IntItem(str: String? = null) : Item() {
            private val value: Int = str?.toInt() ?: 0
            override val isNull: Boolean =
                value == 0

            override operator fun compareTo(other: Item?): Int {
                return other?.let {
                    when (it) {
                        is IntItem -> value.compareTo(it.value)
                        is LongItem -> -1
                        is StringItem -> 1 // 1.1 > 1-sp
                        is ListItem -> 1 // 1.1 > 1-1
                    }
                }
                    ?: if (value == 0) 0 else 1 // 1.0 == 1, 1.1 > 1
            }

            override fun toString(): String = value.toString()

            override fun hashCode(): Int = value

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as IntItem
                if (value != other.value) return false
                return true
            }

            companion object {
                val ZERO = IntItem()
            }
        }

        /**
         * Represents a numeric item in the version item list that can be represented with a long.
         */
        class LongItem(str: String) : Item() {
            private val value: Long = str.toLong()
            override val isNull: Boolean =
                value == 0L

            override operator fun compareTo(other: Item?): Int {
                return other?.let {
                    when (it) {
                        is IntItem -> 1
                        is LongItem -> value.compareTo(it.value)
                        is StringItem -> 1 // 1.1 > 1-sp
                        is ListItem -> 1 // 1.1 > 1-1
                    }
                }
                    ?: if (value == 0L) 0 else 1 // 1.0 == 1, 1.1 > 1
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as LongItem
                if (value != other.value) return false
                return true
            }

            override fun hashCode(): Int =
                (value xor (value ushr 32)).toInt()

            override fun toString(): String = value.toString()
        }

        /**
         * Represents a string in the version item list, usually a qualifier.
         */
        class StringItem(value: String, followedByDigit: Boolean) : Item() {
            companion object {
                private val QUALIFIERS = arrayOf("alpha", "beta", "milestone", "rc", "snapshot", "", "sp")
                private val ALIASES = mapOf("ga" to "", "final" to "", "release" to "", "cr" to "rc")

                /**
                 * A comparable value for the empty-string qualifier. This one is used to determine if a given qualifier makes
                 * the version older than one without a qualifier, or more recent.
                 */
                private val RELEASE_VERSION_INDEX = QUALIFIERS.indexOf("").toString()

                /**
                 * Returns a comparable value for a qualifier.
                 *
                 * This method takes into account the ordering of known qualifiers then unknown qualifiers with lexical
                 * ordering.
                 *
                 * just returning an Integer with the index here is faster, but requires a lot of if/then/else to check for -1
                 * or QUALIFIERS.size and then resort to lexical ordering. Most comparisons are decided by the first character,
                 * so this is still fast. If more characters are needed then it requires a lexical sort anyway.
                 *
                 * @param qualifier
                 * @return an equivalent value that can be used with lexical comparison
                 */
                fun comparableQualifier(qualifier: String): String {
                    val i = QUALIFIERS.indexOf(qualifier)
                    return if (i == -1) QUALIFIERS.size.toString() + "-" + qualifier else i.toString()
                }
            }

            private val value: String =
                (
                        if (followedByDigit && value.length == 1) {
                            // a1 = alpha-1, b1 = beta-1, m1 = milestone-1
                            when (value[0]) {
                                'a' -> "alpha"
                                'b' -> "beta"
                                'm' -> "milestone"
                                else -> value
                            }
                        } else {
                            value
                        }
                        )
                    .let { ALIASES[it] ?: it }

            override val isNull: Boolean
                get() = comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX) == 0

            override operator fun compareTo(other: Item?): Int {
                return other?.let {
                    when (it) {
                        is IntItem, is LongItem -> -1
                        is StringItem -> comparableQualifier(value).compareTo(comparableQualifier(it.value)) // 1.1 > 1-sp
                        is ListItem -> -1 // 1.1 > 1-1
                    }
                }
                    ?: comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX) // 1-rc < 1, 1-ga > 1
            }

            override fun toString(): String = value

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as StringItem

                if (value != other.value) return false

                return true
            }

            override fun hashCode(): Int =
                value.hashCode()
        }

        /**
         * Represents a version list item. This class is used both for the global item list and for sub-lists (which start
         * with '-(number)' in the version specification).
         */
        class ListItem : Item() {
            override val isNull: Boolean
                get() = list.size == 0
            private val list = mutableListOf<Item>()

            fun normalize() {
                for (i in list.size - 1 downTo 0) {
                    val lastItem: Item = list[i]
                    if (lastItem.isNull) {
                        // remove null trailing items: 0, "", empty list
                        list.removeAt(i)
                    } else if (lastItem !is ListItem) {
                        break
                    }
                }
            }

            override operator fun compareTo(other: Item?): Int {
                if (other == null) {
                    if (list.isEmpty()) {
                        return 0 // 1-0 = 1- (normalize) = 1
                    }
                    // Compare the entire list of items with null - not just the first one, MNG-6964
                    for (i in list.indices) {
                        val result: Int = list[i].compareTo(null)
                        if (result != 0) {
                            return result
                        }
                    }
                    return 0
                }

                return when (other) {
                    is IntItem, is LongItem -> -1 // 1-1 < 1.0.x
                    is StringItem -> 1 // 1-1 > 1-sp
                    is ListItem -> {
                        val left: Iterator<Item> = list.iterator()
                        val right: Iterator<Item> = other.list.iterator()
                        while (left.hasNext() || right.hasNext()) {
                            val l: Item? = if (left.hasNext()) left.next() else null
                            val r: Item? = if (right.hasNext()) right.next() else null
                            // if this is shorter, then invert the compare and mul with -1
                            val result = l?.compareTo(r) ?: if (r == null) 0 else -1 * r.compareTo(l)
                            if (result != 0) {
                                return result
                            }
                        }
                        return 0
                    }
                }
            }

            override fun toString(): String {
                var buffer = ""
                for (i in list.indices) {
                    val item = list[i]
                    if (buffer.isNotEmpty()) {
                        buffer += if (item is ListItem) '-' else '.'
                    }
                    buffer += item
                }

                return buffer
            }

            override fun hashCode(): Int =
                list.hashCode()

            fun add(item: Item) {
                list.add(item)
            }

            /**
             * Return the contents in the same format that is used when you call toString() on a List.
             */
            private fun toListString(): String {
                var buffer = "["
                for (i in list.indices) {
                    val item = list[i]
                    if (buffer.length > 1) {
                        buffer += ", "
                    }
                    buffer += (item as? ListItem)?.toListString() ?: item
                }
                buffer += "]"
                return buffer
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as ListItem

                if (list != other.list) return false

                return true
            }
        }
    }

    private fun parseItem(isDigit: Boolean, buf: String): Item {
        if (isDigit) {
            val buffer = stripLeadingZeroes(buf)
            if (buffer.length <= MAX_INTITEM_LENGTH) {
                // lower than 2^31
                return Item.IntItem(buffer)
            } else if (buffer.length <= MAX_LONGITEM_LENGTH) {
                // lower than 2^63
                return Item.LongItem(buffer)
            }
            throw IllegalStateException("Numbers > 2^63 are not supported")
        }
        return Item.StringItem(buf, false)
    }

    override operator fun compareTo(other: KomparableVersion): Int =
        items.compareTo(other.items)

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        other is KomparableVersion && items == other.items

    override fun hashCode(): Int =
        items.hashCode()

    companion object {
        private const val MAX_INTITEM_LENGTH = 9
        private const val MAX_LONGITEM_LENGTH = 18
        private fun stripLeadingZeroes(buf: String?): String {
            if (buf == null || buf.isEmpty()) {
                return "0"
            }
            val idx = buf.indexOfFirst { it != '0' }
            return if (idx >= 0) buf.substring(idx) else buf
        }
    }
}
