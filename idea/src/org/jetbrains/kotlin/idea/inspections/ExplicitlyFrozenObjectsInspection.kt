package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.psi.*

class ExplicitlyFrozenObjectsInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitDeclaration(dcl: KtDeclaration) {
                val varOrVal = dcl as? KtValVarKeywordOwner ?: return
                ReferencesSearch.search(varOrVal, varOrVal.useScope).asSequence().map {
                    it.element
                }.sortedBy {
                    it.textOffset
                }.dropWhile {
                    val parentDotExpression = PsiTreeUtil.getTopmostParentOfType(it, KtDotQualifiedExpression::class.java)
                    val call = parentDotExpression?.selectorExpression as? KtCallExpression
                    val callName = (call?.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                    callName != "freeze"
                }.drop(1).map {
                    PsiTreeUtil.getTopmostParentOfType(it, KtDotQualifiedExpression::class.java) ?: it
                }.filter {
                    (it as? KtExpression)?.readWriteAccess(useResolveForReadWrite = true)?.isWrite == true
                }.map {
                    PsiTreeUtil.getParentOfType(it, KtOperationExpression::class.java) ?: it
                }.map {
                    holder.manager.createProblemDescriptor(
                        it,
                        KotlinBundle.message("this.object.is.frozen.and.can.not.be.mutated"),
                        arrayOf(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        isOnTheFly,
                        false
                    )
                }.forEach {
                    holder.registerProblem(it)
                }
            }
        }
    }
}