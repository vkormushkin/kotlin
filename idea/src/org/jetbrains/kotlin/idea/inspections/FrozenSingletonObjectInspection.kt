/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiExpression
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.psi.*

class FrozenSingletonObjectInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
                //This inspection is not applicable in JVM environment
                if (!isNative()) {
                    return
                }

                //Objects are frozen by default unless they are marked as @ThreadLocal
                val isThreadLocal =
                    declaration.modifierList?.annotationEntries?.any { it -> it.shortName?.asString() == "ThreadLocal" } == true
                if (isThreadLocal) {
                    return
                }

                //Find property declarations
                val vars = declaration.declarations.mapNotNull { it -> it as? KtProperty }
                //Find mutations
                val mutatingExpressions = vars.flatMap {
                    ReferencesSearch.search(it, declaration.resolveScope)
                }.mapNotNull {
                    PsiTreeUtil.getTopmostParentOfType(it.element, KtDotQualifiedExpression::class.java) ?: it.element
                }.filter {
                    (it as? KtExpression)?.readWriteAccess(useResolveForReadWrite = true)?.isWrite == true
                }.map { parentOperation(it) }

                val problems = mutatingExpressions.map { it ->
                    holder.manager.createProblemDescriptor(
                        it,
                        KotlinBundle.message("objects.are.frozen.by.default.and.its.mutation.causes.exception"),
                        arrayOf(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        isOnTheFly,
                        false
                    )
                }

                for (p in problems) {
                    holder.registerProblem(p)
                }
            }

            //since singleton objects are frozen by default in Kotlin/Native only, need to check for that
            private fun isNative(): Boolean {
                //FIXME: add a real check for native environment
                return true;
            }

            //Look up for reference operation to highlight it
            private fun parentOperation(element: PsiElement): PsiElement {
                val parent = PsiTreeUtil.getParentOfType(element, KtOperationExpression::class.java)
                return parent ?: element
            }
        }
    }
}