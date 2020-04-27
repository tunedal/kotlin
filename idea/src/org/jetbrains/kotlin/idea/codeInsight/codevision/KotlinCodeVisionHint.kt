/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.highlighter.markers.OVERRIDDEN_FUNCTION
import org.jetbrains.kotlin.idea.highlighter.markers.OVERRIDDEN_PROPERTY
import org.jetbrains.kotlin.idea.highlighter.markers.SUBCLASSED_CLASS
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import java.awt.event.MouseEvent
import java.text.MessageFormat

interface KotlinCodeVisionHint {
    val regularText: String

    fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?)
}

//todo localization?
const val IMPLEMENTATIONS_HINT_FORMAT = "{0, choice, 1#1 Implementation|2#{0,number} Implementations}"
const val TOO_MANY_IMPLEMENTATIONS_HINT_FORMAT = "{0,number}+ Implementations"

const val INHERITORS_HINT_FORMAT = "{0, choice, 1#1 Inheritor|2#{0,number} Inheritors}"
const val TOO_MANY_INHERITORS_HINT_FORMAT = "{0,number}+ Inheritors"

const val OVERRIDES_HINT_FORMAT = "{0, choice, 1#1 Override|2#{0,number} Overrides}"
const val TOO_MANY_OVERRIDES_HINT_FORMAT = "{0,number}+ Overrides"


const val USAGES_HINT_FORMAT = "{0, choice, 1#1 Usage|2#{0,number} Usages}"
const val TOO_MANY_USAGES_HINT_FORMAT = "{0,number}+ Usages"

//todo WARN - collectors.FUCounterUsageLogger - Cannot record event because group 'kotlin.code.vision' is not registered.
const val FUS_GROUP_ID = "kotlin.code.vision"
const val USAGES_CLICKED_EVENT_ID = "usages.clicked"
const val INHERITORS_CLICKED_EVENT_ID = "inheritors.clicked"
const val IMPLEMENTATIONS_CLICKED_EVENT_ID = "implementations.clicked" // todo OVERRIDINGS?
const val SETTING_CLICKED_EVENT_ID = "setting.clicked"

class Usages(usagesNum: Int, limitReached: Boolean) : KotlinCodeVisionHint {
    override val regularText: String = MessageFormat.format(
        if (limitReached) TOO_MANY_USAGES_HINT_FORMAT else USAGES_HINT_FORMAT, usagesNum
    )

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, USAGES_CLICKED_EVENT_ID)
        GotoDeclarationAction.startFindUsages(editor, editor.project!!, element)
    }
}

class FunctionOverrides(overridesNum: Int, limitReached: Boolean) : KotlinCodeVisionHint {
    override val regularText: String = MessageFormat.format(
        if (limitReached) TOO_MANY_OVERRIDES_HINT_FORMAT else OVERRIDES_HINT_FORMAT, overridesNum
    )

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val data = FeatureUsageData().addData("location", "function")
        FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data)
        val navigationHandler = OVERRIDDEN_FUNCTION.navigationHandler
        navigationHandler.navigate(event, (element as KtFunction).nameIdentifier)
    }
}

class FunctionImplementations(overridesNum: Int, limitReached: Boolean) :
    KotlinCodeVisionHint {
    override val regularText: String = MessageFormat.format(
        if (limitReached) TOO_MANY_IMPLEMENTATIONS_HINT_FORMAT else IMPLEMENTATIONS_HINT_FORMAT, overridesNum
    )

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val data = FeatureUsageData().addData("location", "function")
        FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data)
        val navigationHandler = OVERRIDDEN_FUNCTION.navigationHandler
        navigationHandler.navigate(event, (element as KtFunction).nameIdentifier)
    }
}

class PropertyOverrides(overridesNum: Int, limitReached: Boolean) : KotlinCodeVisionHint {
    override val regularText: String = MessageFormat.format(
        if (limitReached) TOO_MANY_OVERRIDES_HINT_FORMAT else OVERRIDES_HINT_FORMAT, overridesNum
    ) //todo inconsistent with the popup

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val data = FeatureUsageData().addData("location", "property")
        FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data)
        val navigationHandler = OVERRIDDEN_PROPERTY.navigationHandler
        navigationHandler.navigate(event, (element as KtProperty).nameIdentifier)
    }
}

class ClassInheritors(inheritorsNum: Int, limitReached: Boolean) : KotlinCodeVisionHint {
    override val regularText: String = MessageFormat.format(
        if (limitReached) TOO_MANY_INHERITORS_HINT_FORMAT else INHERITORS_HINT_FORMAT, inheritorsNum
    )

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val data = FeatureUsageData().addData("location", "class")
        FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, INHERITORS_CLICKED_EVENT_ID, data)
        val navigationHandler = SUBCLASSED_CLASS.navigationHandler
        navigationHandler.navigate(event, (element as KtClass).nameIdentifier)
    }
}

class InterfaceImplementations(implNum: Int, limitReached: Boolean) : KotlinCodeVisionHint {
    override val regularText: String = MessageFormat.format(
        if (limitReached) TOO_MANY_IMPLEMENTATIONS_HINT_FORMAT else IMPLEMENTATIONS_HINT_FORMAT, implNum
    )

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val data = FeatureUsageData().addData("location", "interface")
        FUCounterUsageLogger.getInstance().logEvent(editor.project, FUS_GROUP_ID, IMPLEMENTATIONS_CLICKED_EVENT_ID, data)
        val navigationHandler = SUBCLASSED_CLASS.navigationHandler
        navigationHandler.navigate(event, (element as KtClass).nameIdentifier)
    }
}

class SettingsHint : KotlinCodeVisionHint {
    override val regularText: String = "Settings..."

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val project = element.project
        FUCounterUsageLogger.getInstance().logEvent(project, FUS_GROUP_ID, SETTING_CLICKED_EVENT_ID)
        InlayHintsConfigurable.showSettingsDialogForLanguage(project, element.language)
    }
}