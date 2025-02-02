package fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.dict.QueryBuilderPropertyAlias;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.dict.QueryBuilderRelation;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.util.MatcherUtil;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.util.QueryBuilderUtil;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class QueryBuilderGotoDeclarationHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(PsiElement psiElement, int offset, Editor editor) {

        if (!Symfony2ProjectComponent.isEnabled(psiElement) || !(psiElement.getContext() instanceof StringLiteralExpression context)) {
            return new PsiElement[0];
        }

        List<PsiElement> psiElements = new ArrayList<>();

        attachJoinGoto(context, psiElements);
        attachPartialGoto(context, psiElements, offset);

        // $qb->expr()->in('')
        attachExprGoto(context, psiElements);

        // $qb->from('', '', '<foo>');
        attachFromIndexGoto(context, psiElements);

        return psiElements.toArray(new PsiElement[0]);
    }

    private void attachPartialGoto(@NotNull StringLiteralExpression psiElement, @NotNull List<PsiElement> targets, int offset) {
        MethodMatcher.MethodMatchParameter methodMatchParameter = MatcherUtil.matchWhere(psiElement);
        if (methodMatchParameter == null) {
            methodMatchParameter = MatcherUtil.matchField(psiElement);
        }

        if (methodMatchParameter == null) {
            return;
        }

        int calulatedOffset = offset - psiElement.getTextRange().getStartOffset() - 1;
        if (calulatedOffset < 0) {
            calulatedOffset = 0;
        }

        String contents = psiElement.getContents();
        String fieldString = QueryBuilderUtil.getFieldString(contents, calulatedOffset);
        if (fieldString != null) {
            QueryBuilderMethodReferenceParser qb = QueryBuilderCompletionContributor.getQueryBuilderParser(methodMatchParameter.getMethodReference());
            QueryBuilderScopeContext collect = qb.collect();
            for(Map.Entry<String, QueryBuilderPropertyAlias> entry: collect.getPropertyAliasMap().entrySet()) {
                if(entry.getKey().equals(fieldString)) {
                    targets.addAll(entry.getValue().getPsiTargets());
                }
            }
        }
    }

    private void attachJoinGoto(StringLiteralExpression psiElement, List<PsiElement> targets) {

        MethodMatcher.MethodMatchParameter methodMatchParameter = MatcherUtil.matchJoin(psiElement);
        if(methodMatchParameter == null) {
            return;
        }

        QueryBuilderMethodReferenceParser qb = QueryBuilderCompletionContributor.getQueryBuilderParser(methodMatchParameter.getMethodReference());
        if(qb == null) {
            return;
        }

        String[] joinSplit = StringUtils.split(psiElement.getContents(), ".");
        if(joinSplit.length != 2) {
            return;
        }

        QueryBuilderScopeContext collect = qb.collect();
        if(!collect.getRelationMap().containsKey(joinSplit[0])) {
            return;
        }

        List<QueryBuilderRelation> relations = collect.getRelationMap().get(joinSplit[0]);
        Project project = psiElement.getProject();

        for (QueryBuilderRelation relation: relations) {
            if (joinSplit[1].equals(relation.getFieldName()) && relation.getTargetEntity() != null) {
                PhpClass phpClass = PhpElementsUtil.getClassInterface(project, relation.getTargetEntity());
                if (phpClass != null) {
                    targets.add(phpClass);
                }
            }
        }
    }

    private void attachExprGoto(StringLiteralExpression psiElement, List<PsiElement> targets) {
        MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement, 0)
            .withSignature(QueryBuilderCompletionContributor.EXPR)
            .match();

        if(methodMatchParameter == null) {
            return;
        }

        // simple resolve query inline instance usage
        // $qb->expr()->in('')
        MethodReference methodReference = methodMatchParameter.getMethodReference();
        PsiElement methodReferenceChild = methodReference.getFirstChild();
        if(!(methodReferenceChild instanceof MethodReference)) {
            return;
        }
        QueryBuilderMethodReferenceParser qb = QueryBuilderCompletionContributor.getQueryBuilderParser((MethodReference) methodReferenceChild);

        String propertyContent = psiElement.getContents();
        QueryBuilderScopeContext collect = qb.collect();
        for(Map.Entry<String, QueryBuilderPropertyAlias> entry: collect.getPropertyAliasMap().entrySet()) {
            if(entry.getKey().equals(propertyContent)) {
                targets.addAll(entry.getValue().getPsiTargets());
            }
        }
    }

    private void attachFromIndexGoto(StringLiteralExpression psiElement, List<PsiElement> targets) {

        MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement, 2)
            .withSignature("\\Doctrine\\ORM\\QueryBuilder", "from")
            .match();

        if(methodMatchParameter == null) {
            return;
        }

        QueryBuilderMethodReferenceParser qb = QueryBuilderCompletionContributor.getQueryBuilderParser(methodMatchParameter.getMethodReference());
        if(qb == null) {
            return;
        }

        QueryBuilderScopeContext collect = qb.collect();
        String propertyContent = psiElement.getContents();
        for(Map.Entry<String, QueryBuilderPropertyAlias> entry: collect.getPropertyAliasMap().entrySet()) {
            if(entry.getKey().equals(propertyContent)) {
                targets.addAll(entry.getValue().getPsiTargets());
            }
        }

    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }

}
