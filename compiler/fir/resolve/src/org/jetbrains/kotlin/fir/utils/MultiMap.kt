/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.utils

import com.google.common.collect.ArrayListMultimap

interface MultiMap<K, out V> {
    val size: Int
    fun isEmpty(): Boolean
    fun containsKey(key: K): Boolean
    fun containsValue(value: @UnsafeVariance V): Boolean
    operator fun get(key: K): List<V>

    val values: Collection<V>
    val entries: Collection<Map.Entry<K, V>>
}

interface MutableMultiMap<K, V> : MultiMap<K, V> {
    fun put(key: K, value: V): Boolean
    fun removeAll(key: K): List<V>

    fun remove(key: K, value: V): Boolean

    fun clear()

    override val values: MutableCollection<V>
    override val entries: MutableCollection<MutableMap.MutableEntry<K, V>>
}

class MultiMapImpl<K, V> : MutableMultiMap<K, V> {
    private val map = ArrayListMultimap.create<K, V>()

    override val size: Int
        get() = map.size()

    override fun isEmpty(): Boolean {
        return map.isEmpty
    }

    override fun containsKey(key: K): Boolean {
        return map.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        return map.containsValue(value)
    }

    override fun get(key: K): List<V> {
        return map[key]
    }

    override fun put(key: K, value: V): Boolean {
        return map.put(key, value)
    }

    override fun removeAll(key: K): List<V> {
        return map.removeAll(key)
    }

    override fun remove(key: K, value: V): Boolean {
        return map.remove(key, value)
    }

    override fun clear() {
        map.clear()
    }

    override val values: MutableCollection<V>
        get() = map.values()

    override val entries: MutableCollection<MutableMap.MutableEntry<K, V>>
        get() = map.entries()
}
