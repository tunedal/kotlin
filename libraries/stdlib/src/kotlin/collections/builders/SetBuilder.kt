/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.collections.builders

/*@PublishedApi
internal */class SetBuilder<E> internal constructor(
    private val backing: MapBuilder<E, *>
) : MutableSet<E>, AbstractMutableCollection<E>() {

    constructor() : this(MapBuilder<E, Nothing>())

    constructor(initialCapacity: Int) : this(MapBuilder<E, Nothing>(initialCapacity))

    fun build(): Set<E> {
        backing.build()
        return this
    }

    override val size: Int get() = backing.size
    override fun isEmpty(): Boolean = backing.isEmpty()
    override fun contains(element: E): Boolean = backing.containsKey(element)
    override fun clear() = backing.clear()
    override fun add(element: E): Boolean = backing.addKey(element) >= 0
    override fun remove(element: E): Boolean = backing.removeKey(element) >= 0
    override fun iterator(): MutableIterator<E> = backing.keysIterator()

    override fun containsAll(elements: Collection<E>): Boolean {
        val it = elements.iterator()
        while (it.hasNext()) {
            if (!contains(it.next()))
                return false
        }
        return true
    }

    override fun addAll(elements: Collection<E>): Boolean {
        val it = elements.iterator()
        var updated = false
        while (it.hasNext()) {
            if (add(it.next()))
                updated = true
        }
        return updated
    }

    override fun equals(other: Any?): Boolean {
        return other === this ||
                (other is Set<*>) &&
                contentEquals(
                    @Suppress("UNCHECKED_CAST") (other as Set<E>))
    }

    override fun hashCode(): Int {
        var result = 0
        val it = iterator()
        while (it.hasNext()) {
            result += it.next().hashCode()
        }
        return result
    }

    override fun toString(): String = collectionToString()

    // ---------------------------- private ----------------------------

    private fun contentEquals(other: Set<E>): Boolean = size == other.size && containsAll(other)
}

internal fun <E> Collection<E>.collectionToString(): String {
    val sb = StringBuilder(2 + size * 3)
    sb.append("[")
    var i = 0
    val it = iterator()
    while (it.hasNext()) {
        if (i > 0) sb.append(", ")
        val next = it.next()
        if (next == this) sb.append("(this Collection)") else sb.append(next)
        i++
    }
    sb.append("]")
    return sb.toString()
}