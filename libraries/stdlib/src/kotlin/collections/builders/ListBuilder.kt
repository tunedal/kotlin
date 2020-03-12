/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.collections.builders

@PublishedApi
internal class ListBuilder<E> private constructor(
    private var array: Array<E>,
    private var offset: Int,
    private var length: Int,
    private val backing: ListBuilder<E>?
) : MutableList<E>, RandomAccess, AbstractMutableCollection<E>() {

    constructor() : this(10)

    constructor(initialCapacity: Int) : this(
        arrayOfUninitializedElements(initialCapacity), 0, 0, null)

    fun build(): List<E> {
        if (backing != null) throw IllegalStateException() // just in case somebody casts subList to ListBuilder
        val array = this.array
//        this.array = null
        return ImmutableList(length, array, offset)
    }

    override val size: Int
        get() = length

    override fun isEmpty(): Boolean = length == 0

    override fun get(index: Int): E {
        checkIndex(index)
        return array[offset + index]
    }

    override operator fun set(index: Int, element: E): E {
        checkIndex(index)
        val old = array[offset + index]
        array[offset + index] = element
        return old
    }

    override fun contains(element: E): Boolean {
        var i = 0
        while (i < length) {
            if (array[offset + i] == element) return true
            i++
        }
        return false
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        val it = elements.iterator()
        while (it.hasNext()) {
            if (!contains(it.next()))return false
        }
        return true
    }

    override fun indexOf(element: E): Int {
        var i = 0
        while (i < length) {
            if (array[offset + i] == element) return i
            i++
        }
        return -1
    }

    override fun lastIndexOf(element: E): Int {
        var i = length - 1
        while (i >= 0) {
            if (array[offset + i] == element) return i
            i--
        }
        return -1
    }

    override fun iterator(): MutableIterator<E> = Itr(this, 0)
    override fun listIterator(): MutableListIterator<E> = Itr(this, 0)

    override fun listIterator(index: Int): MutableListIterator<E> {
        checkInsertIndex(index)
        return Itr(this, index)
    }

    override fun add(element: E): Boolean {
        addAtInternal(offset + length, element)
        return true
    }

    override fun add(index: Int, element: E) {
        checkInsertIndex(index)
        addAtInternal(offset + index, element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        val n = elements.size
        addAllInternal(offset + length, elements, n)
        return n > 0
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        checkInsertIndex(index)
        val n = elements.size
        addAllInternal(offset + index, elements, n)
        return n > 0
    }

    override fun clear() {
        removeRangeInternal(offset, length)
    }

    override fun removeAt(index: Int): E {
        checkIndex(index)
        return removeAtInternal(offset + index)
    }

    override fun remove(element: E): Boolean {
        val i = indexOf(element)
        if (i >= 0) removeAt(i)
        return i >= 0
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        return retainOrRemoveAllInternal(offset, length, elements, false) > 0
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        return retainOrRemoveAllInternal(offset, length, elements, true) > 0
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        checkInsertIndex(fromIndex)
        checkInsertIndexFrom(toIndex, fromIndex)
        return ListBuilder(array, offset + fromIndex, toIndex - fromIndex, this)
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (backing != null) throw IllegalStateException() // just in case somebody casts subList to ListBuilder
        if (minCapacity > array.size) {
            var newSize = array.size * 3 / 2
            if (minCapacity > newSize)
                newSize = minCapacity
            array = array.copyOfUninitializedElements(newSize)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other === this ||
                (other is List<*>) && contentEquals(other)
    }

    override fun hashCode(): Int {
        var result = 1
        var i = 0
        while (i < length) {
            val nextElement = array[offset + i]
            val nextHash = if (nextElement != null) nextElement.hashCode() else 0
            result = result * 31 + nextHash
            i++
        }
        return result
    }

    override fun toString(): String {
        return this.array.subarrayContentToString(offset, length)
    }

    // ---------------------------- private ----------------------------

    private fun ensureExtraCapacity(n: Int) {
        ensureCapacity(length + n)
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= length) throw IndexOutOfBoundsException()
    }

    private fun checkInsertIndex(index: Int) {
        if (index < 0 || index > length) throw IndexOutOfBoundsException()
    }

    private fun checkInsertIndexFrom(index: Int, fromIndex: Int) {
        if (index < fromIndex || index > length) throw IndexOutOfBoundsException()
    }

    private fun contentEquals(other: List<*>): Boolean {
        if (length != other.size) return false
        var i = 0
        while (i < length) {
            if (array[offset + i] != other[i]) return false
            i++
        }
        return true
    }

    private fun insertAtInternal(i: Int, n: Int) {
        ensureExtraCapacity(n)
        array.copyInto(array, startIndex = i, endIndex = offset + length, destinationOffset = i + n)
        length += n
    }

    private fun addAtInternal(i: Int, element: E) {
        if (backing != null) {
            backing.addAtInternal(i, element)
            array = backing.array
            length++
        } else {
            insertAtInternal(i, 1)
            array[i] = element
        }
    }

    private fun addAllInternal(i: Int, elements: Collection<E>, n: Int) {
        if (backing != null) {
            backing.addAllInternal(i, elements, n)
            array = backing.array
            length += n
        } else {
            insertAtInternal(i, n)
            var j = 0
            val it = elements.iterator()
            while (j < n) {
                array[i + j] = it.next()
                j++
            }
        }
    }

    private fun removeAtInternal(i: Int): E {
        if (backing != null) {
            val old = backing.removeAtInternal(i)
            length--
            return old
        } else {
            val old = array[i]
            array.copyInto(array, startIndex = i + 1, endIndex = offset + length, destinationOffset = i)
            array.resetAt(offset + length - 1)
            length--
            return old
        }
    }

    private fun removeRangeInternal(rangeOffset: Int, rangeLength: Int) {
        if (backing != null) {
            backing.removeRangeInternal(rangeOffset, rangeLength)
        } else {
            array.copyInto(array, startIndex = rangeOffset + rangeLength, endIndex = length, destinationOffset = rangeOffset)
            array.resetRange(fromIndex = length - rangeLength, toIndex = length)
        }
        length -= rangeLength
    }

    /** Retains elements if [retain] == true and removes them it [retain] == false. */
    private fun retainOrRemoveAllInternal(rangeOffset: Int, rangeLength: Int, elements: Collection<E>, retain: Boolean): Int {
        if (backing != null) {
            val removed = backing.retainOrRemoveAllInternal(rangeOffset, rangeLength, elements, retain)
            length -= removed
            return removed
        } else {
            var i = 0
            var j = 0
            while (i < rangeLength) {
                if (elements.contains(array[rangeOffset + i]) == retain) {
                    array[rangeOffset + j++] = array[rangeOffset + i++]
                } else {
                    i++
                }
            }
            val removed = rangeLength - j
            array.copyInto(array, startIndex = rangeOffset + rangeLength, endIndex = length, destinationOffset = rangeOffset + j)
            array.resetRange(fromIndex = length - removed, toIndex = length)
            length -= removed
            return removed
        }
    }

    private class Itr<E> : MutableListIterator<E> {
        private val list: ListBuilder<E>
        private var index: Int
        private var lastIndex: Int

        constructor(list: ListBuilder<E>, index: Int) {
            this.list = list
            this.index = index
            this.lastIndex = -1
        }

        override fun hasPrevious(): Boolean = index > 0
        override fun hasNext(): Boolean = index < list.length

        override fun previousIndex(): Int = index - 1
        override fun nextIndex(): Int = index

        override fun previous(): E {
            if (index <= 0) throw NoSuchElementException()
            lastIndex = --index
            return list.array[list.offset + lastIndex]
        }

        override fun next(): E {
            if (index >= list.length) throw NoSuchElementException()
            lastIndex = index++
            return list.array[list.offset + lastIndex]
        }

        override fun set(element: E) {
            list.checkIndex(lastIndex)
            list.array[list.offset + lastIndex] = element
        }

        override fun add(element: E) {
            list.add(index++, element)
            lastIndex = -1
        }

        override fun remove() {
            check(lastIndex != -1) { "Call next() or previous() before removing element from the iterator." }
            list.removeAt(lastIndex)
            index = lastIndex
            lastIndex = -1
        }
    }
}

private class ImmutableList<E>(
    override val size: Int,
    private val array: Array<E>,
    private val offset: Int
) : List<E> {

    override fun contains(element: E): Boolean {
        return indexOf(element) != -1
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return elements.all { contains(it) }
    }

    override fun get(index: Int): E {
        AbstractList.checkElementIndex(index, size)
        return array[offset + index]
    }

    override fun indexOf(element: E): Int {
        for (index in 0 until size) {
            if (element == array[offset + index]) return index
        }
        return -1
    }

    override fun isEmpty(): Boolean {
        return size == 0
    }

    override fun lastIndexOf(element: E): Int {
        for (index in size - 1 downTo 0) {
            if (element == array[offset + index]) return index
        }
        return -1
    }

    override fun iterator(): Iterator<E> = Itr(this, 0)
    override fun listIterator(): ListIterator<E> = Itr(this, 0)

    override fun listIterator(index: Int): ListIterator<E> {
        AbstractList.checkPositionIndex(index, size)
        return Itr(this, index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<E> {
        AbstractList.checkRangeIndexes(fromIndex, toIndex, size)
        return ImmutableList(toIndex - fromIndex, array, offset + fromIndex)
    }

    private class Itr<E> : ListIterator<E> {
        private val list: ImmutableList<E>
        private var index: Int

        constructor(list: ImmutableList<E>, index: Int) {
            this.list = list
            this.index = index
        }

        override fun hasPrevious(): Boolean = index > 0
        override fun hasNext(): Boolean = index < list.size

        override fun previousIndex(): Int = index - 1
        override fun nextIndex(): Int = index

        override fun previous(): E {
            if (index <= 0) throw NoSuchElementException()
            return list.array[list.offset + --index]
        }

        override fun next(): E {
            if (index >= list.size) throw NoSuchElementException()
            return list.array[list.offset + index++]
        }
    }
}

internal fun <E> arrayOfUninitializedElements(size: Int): Array<E> {
    @Suppress("UNCHECKED_CAST")
    return arrayOfNulls<Any?>(size) as Array<E>
}

@kotlin.internal.InlineOnly
internal inline fun <T> Array<out T>.subarrayContentToString(offset: Int, length: Int): String {
    val sb = StringBuilder(2 + length * 3)
    sb.append("[")
    var i = 0
    while (i < length) {
        if (i > 0) sb.append(", ")
        sb.append(this[offset + i])
        i++
    }
    sb.append("]")
    return sb.toString()
}

internal fun <T> Array<T>.copyOfUninitializedElements(newSize: Int): Array<T> {
    return copyOfUninitializedElements(0, newSize)
}

internal fun <T> Array<T>.copyOfUninitializedElements(fromIndex: Int, toIndex: Int): Array<T> {
    val newSize = toIndex - fromIndex
    if (newSize < 0) {
        throw IllegalArgumentException("$fromIndex > $toIndex")
    }
    val result = arrayOfUninitializedElements<T>(newSize)
    this.copyInto(result, 0, fromIndex, toIndex.coerceAtMost(size))
    return result
}

@kotlin.internal.InlineOnly
internal inline fun <E> Array<E>.resetAt(index: Int) {
    @Suppress("UNCHECKED_CAST")
    (this as Array<Any?>)[index] = null
}

internal fun <E> Array<E>.resetRange(fromIndex: Int, toIndex: Int) {
    for (index in fromIndex until toIndex) resetAt(index)
}