/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.AttributesTransformerPresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingMethod
import org.jetbrains.kotlin.idea.search.declarationsSearch.toPossiblyFakeLightMethods
import org.jetbrains.kotlin.idea.search.usagesSearch.searchReferencesOrMethodReferences
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.SmartList


@Suppress("UnstableApiUsage")
class KotlinCodeVisionHintsCollector(editor: Editor, val settings: KotlinCodeVisionSettings) : FactoryInlayHintsCollector(editor) {

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (!isElementOfInterest(element))
            return true

        val hints: MutableList<KotlinCodeVisionHint> = SmartList() // todo: pair?

        // todo: consider too-many-usages, in-background-search, read-lock
        if (settings.showUsages)
            searchUsages(element)?.let { hints += it }

        if (settings.showImplementations) {
            when (element) {
                is KtFunction -> searchFunctionOverrides(element)?.let { hints += it }
                is KtClass -> searchClassInheritors(element)?.let { hints += it }
                is KtProperty -> searchPropertyOverriding(element)?.let { hints += it }
            }
        }

        if (hints.isNotEmpty())
            prepareBlockElements(element, editor, hints, sink)

        return true
    }

    private fun searchFunctionOverrides(function: KtFunction): KotlinCodeVisionHint? {
        return LightClassUtil.getLightClassMethod(function)?.let { it ->
            val overridingNum = OverridingMethodsSearch.search(it, true).count()
            if (overridingNum > 0) {
                if (function.isAbstract()) FunctionImplementations(overridingNum) else FunctionOverrides(overridingNum)
            } else null
        }
    }

    private fun searchClassInheritors(clazz: KtClass): KotlinCodeVisionHint? {
        return clazz.toLightClass()?.let {
            val inheritorsNum = DirectClassInheritorsSearch.search(it, clazz.useScope, true).count()
            if (inheritorsNum > 0) {
                if (clazz.isInterface()) InterfaceImplementations(inheritorsNum) else ClassInheritors(inheritorsNum)
            } else null
        }
    }

    private fun searchPropertyOverriding(property: KtProperty): KotlinCodeVisionHint? {
        var overridingNum = 0
        for (method in property.toPossiblyFakeLightMethods()) {
            method.forEachOverridingMethod {
                overridingNum++
                true
            }
        }
        return if (overridingNum > 0) PropertyOverrides(overridingNum) else null
    }

    private fun searchUsages(element: PsiElement): Usages? {
        val usagesNum = element.searchReferencesOrMethodReferences().count()
        return if (usagesNum > 0) Usages(usagesNum) else null
    }

    @Suppress("GrazieInspection")
    private fun prepareBlockElements(element: PsiElement, editor: Editor, hints: MutableList<KotlinCodeVisionHint>, sink: InlayHintsSink) {
        assert(hints.isNotEmpty()) { "Attempt to build block elements whereas hints don't exist" }
        assert(hints.size <= 2) { "Hints other than usages-implementations are not expected" }

        val offset = element.textRange.startOffset
        val line = editor.document.getLineNumber(offset)
        val lineStart = editor.document.getLineStartOffset(line)
        val indent = offset - lineStart

        /*
         * presentations: <indent>[<Usages>][<space><Inheritors>]
         * hints:                  hint[0]             hint[1]
         */
        val presentations = arrayOfNulls<InlayPresentation>(hints.size * 2) // 2 or 4
        presentations[0] = factory.text(StringUtil.repeat(" ", indent))
        var pInd = 1
        for (hInd in hints.indices) { // handling usages & inheritors
            val hint: KotlinCodeVisionHint = hints[hInd]
            if (hInd != 0)
                presentations[pInd++] = factory.text(" ")

            presentations[pInd++] = createPresentation(factory, element, editor, hint) // either Usages or Inheritors
        }

        val filledPresentations = presentations.requireNoNulls()

        val seq = factory.seq(*filledPresentations)
        val withAppearingSettings = factory.changeOnHover(seq, {
            val spaceAndSettings = arrayOf(factory.text(" "), createSettings(factory, element, editor))
            val withSettings = ArrayUtil.mergeArrays(filledPresentations, spaceAndSettings)
            factory.seq(*withSettings)
        }) { true }

        sink.addBlockElement(lineStart, relatesToPrecedingText = true, showAbove = true, 0, withAppearingSettings)
    }

    private fun isElementOfInterest(element: PsiElement): Boolean = element is KtClass || element is KtFunction || element is KtProperty

    private fun createPresentation(
        factory: PresentationFactory, element: PsiElement, editor: Editor, result: KotlinCodeVisionHint
    ): InlayPresentation {
        val text = factory.smallText(result.regularText)
        return factory.changeOnHover(text, {
            val onClick = factory.onClick(text, MouseButton.Left)
            { event, _ -> result.onClick(editor, element, event) }
            applyReferenceColor(onClick)
        }) { true }
    }

    private fun applyReferenceColor(presentation: InlayPresentation): InlayPresentation {
        return AttributesTransformerPresentation(presentation) {
            val attributes = EditorColorsManager.getInstance()
                .globalScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).clone()
            attributes.apply { effectType = EffectType.LINE_UNDERSCORE }
        }
    }

    private fun createSettings(factory: PresentationFactory, element: PsiElement, editor: Editor): InlayPresentation {
        return createPresentation(factory, element, editor, SettingsHint())
    }


}