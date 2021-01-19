/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.psi.KtOperationExpression

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
                val references = vars.flatMap { ReferencesSearch.search(it, declaration.resolveScope) }
                val mutatedReferences = references.filter {
                    (it as? KtSimpleNameReference)?.element?.readWriteAccess(useResolveForReadWrite = true)?.isWrite == true
                }

                val mutatingExpressions = references.map { parentOperation(it.element) }

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
                val parent = element.parent
                if (parent == null || element is KtOperationExpression) {
                    return element
                }
                return parentOperation(parent)
            }
        }
    }
}