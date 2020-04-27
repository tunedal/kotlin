/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.resolve.firSymbolProvider
import org.jetbrains.kotlin.fir.resolve.getSymbolByTypeRef
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.utils.*
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
            for (metaAnnotation in extension.metaAnnotations) {
                extensionsWithMetaAnnotations.put(metaAnnotation, extension)
            }
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

    fun registerUserDefinedAnnotation(annotation: FirRegularClass) {
        require(annotation.classKind == ClassKind.ANNOTATION_CLASS)
        val fqName = annotation.symbol.classId.asSingleFqName()
        require(fqName in metaAnnotations)
        val extensions = extensionsWithMetaAnnotations[fqName]
        if (extensions.isEmpty()) return
        for (extension in extensions) {
            val registeredExtensions = this[extension::class]

            @Suppress("UNCHECKED_CAST")
            val map = when (extension.mode) {
                FirExtensionPoint.Mode.ANNOTATED_ELEMENT -> registeredExtensions.extensionsWithAnnotatedMode
                FirExtensionPoint.Mode.ALL_IN_ANNOTATED_ELEMENT -> registeredExtensions.extensionsWithAllInAnnotatedMode
                FirExtensionPoint.Mode.ALL -> throw IllegalStateException("Extension with mode ALL can't be subscribed to meta annotation")
            } as MutableMultiMap<AnnotationFqn, FirExtensionPoint>
            map.put(fqName, extension)
        }
    }

    val annotations: Set<AnnotationFqn>
        get() = _annotations
    private val _annotations: MutableSet<AnnotationFqn> = mutableSetOf()

    val metaAnnotations: Set<AnnotationFqn>
        get() = _metaAnnotations
    private val _metaAnnotations: MutableSet<AnnotationFqn> = mutableSetOf()

    private val extensionsWithMetaAnnotations: MutableMultiMap<AnnotationFqn, FirExtensionPoint> = MultiMapImpl()

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