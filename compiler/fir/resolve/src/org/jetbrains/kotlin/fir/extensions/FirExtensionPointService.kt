/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.resolve.firSymbolProvider
import org.jetbrains.kotlin.fir.resolve.getSymbolByTypeRef
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.utils.ComponentArrayOwner
import org.jetbrains.kotlin.fir.utils.ComponentTypeRegistry
import org.jetbrains.kotlin.fir.utils.MultiMap
import org.jetbrains.kotlin.fir.utils.MultiMapImpl
import org.jetbrains.kotlin.name.FqName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class FirRegisteredExtension<P : FirExtensionPoint>(
    val extensionsWithAllMode: List<P>,
    val extensionsWithAnnotatedMode: MultiMap<AnnotationFqn, P>,
    val extensionsWithAllInAnnotatedMode: MultiMap<AnnotationFqn, P>
) {
    fun isEmpty(): Boolean {
        return extensionsWithAllMode.isEmpty() && extensionsWithAnnotatedMode.isEmpty() && extensionsWithAllInAnnotatedMode.isEmpty()
    }
}

class FirExtensionPointService(
    val session: FirSession
) : ComponentArrayOwner<FirExtensionPoint, FirRegisteredExtension<*>>(), FirSessionComponent {
    companion object : ComponentTypeRegistry<FirExtensionPoint, FirRegisteredExtension<*>>() {
        inline fun <reified P : FirExtensionPoint, V : FirRegisteredExtension<P>> registeredExtensions(): ReadOnlyProperty<FirExtensionPointService, ExtensionsAccessor<P>> {
            val accessor = generateAccessor<V, P>(P::class)
            return object : ReadOnlyProperty<FirExtensionPointService, ExtensionsAccessor<P>> {
                override fun getValue(thisRef: FirExtensionPointService, property: KProperty<*>): ExtensionsAccessor<P> {
                    return ExtensionsAccessor(thisRef.session, accessor.getValue(thisRef, property))
                }
            }
        }
    }

    override val typeRegistry: ComponentTypeRegistry<FirExtensionPoint, FirRegisteredExtension<*>>
        get() = Companion

    fun <P : FirExtensionPoint> registerExtensions(extensionClass: KClass<P>, extensionFactories: List<FirExtensionPoint.Factory<P>>) {
        val extensions = extensionFactories.map { it.create(session) }

        val extensionsWithAllMode = mutableListOf<P>()
        val extensionsWithAnnotatedMode = MultiMapImpl<AnnotationFqn, P>()
        val extensionsWithAllInAnnotatedMode = MultiMapImpl<AnnotationFqn, P>()
        for (extension in extensions) {
            _metaAnnotations += extension.metaAnnotations
            _annotations += extension.annotations
            when (extension.mode) {
                FirExtensionPoint.Mode.ANNOTATED_ELEMENT -> {
                    for (annotation in extension.annotations) {
                        extensionsWithAnnotatedMode.put(annotation, extension)
                    }
                }
                FirExtensionPoint.Mode.ALL_IN_ANNOTATED_ELEMENT -> {
                    for (annotation in extension.annotations) {
                        extensionsWithAllInAnnotatedMode.put(annotation, extension)
                    }
                }
                FirExtensionPoint.Mode.ALL -> extensionsWithAllMode += extension
            }
            for (annotation in extension.annotations) {
                extensionsWithAnnotatedMode.put(annotation, extension)
            }
        }
        registerComponent(
            extensionClass,
            FirRegisteredExtension(
                extensionsWithAllMode,
                extensionsWithAnnotatedMode,
                extensionsWithAllInAnnotatedMode
            )
        )
    }

    val annotations: Set<AnnotationFqn>
        get() = _annotations
    private val _annotations: MutableSet<AnnotationFqn> = mutableSetOf()

    val metaAnnotations: Set<AnnotationFqn>
        get() = _metaAnnotations
    private val _metaAnnotations: MutableSet<AnnotationFqn> = mutableSetOf()

    class ExtensionsAccessor<P : FirExtensionPoint>(
        private val session: FirSession,
        private val extensions: FirRegisteredExtension<P>
    ) {
        fun forDeclaration(declaration: FirAnnotationContainer, owners: Collection<FirAnnotationContainer>): Collection<P> {
            if (extensions.isEmpty()) return emptySet()
            if (declaration.annotations.isEmpty() || extensions.extensionsWithAnnotatedMode.isEmpty() && extensions.extensionsWithAllInAnnotatedMode.isEmpty()) {
                return extensions.extensionsWithAllMode
            }

            return LinkedHashSet(extensions.extensionsWithAllMode).apply {
                collectExtensions(this, declaration, extensions.extensionsWithAnnotatedMode)
                for (owner in owners) {
                    collectExtensions(this, owner, extensions.extensionsWithAllInAnnotatedMode)
                }
            }
        }

        private val FirAnnotationCall.fqName: FqName?
            get() {
                val symbol = session.firSymbolProvider.getSymbolByTypeRef<FirRegularClassSymbol>(annotationTypeRef) ?: return null
                return symbol.classId.asSingleFqName()
            }

        private fun collectExtensions(result: MutableSet<P>, declaration: FirAnnotationContainer, extensions: MultiMap<AnnotationFqn, P>) {
            for (annotation in declaration.annotations) {
                val fqName = annotation.fqName ?: continue
                result += extensions[fqName]
            }
        }
    }
}

val FirSession.extensionPointService: FirExtensionPointService by FirSession.sessionComponentAccessor()