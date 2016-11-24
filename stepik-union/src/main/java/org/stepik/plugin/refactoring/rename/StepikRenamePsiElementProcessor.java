package org.stepik.plugin.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import org.jetbrains.annotations.NotNull;

import static org.stepik.plugin.utils.ProjectPsiFilesUtils.isNotMovableOrRenameElement;

/**
 * @author meanmail
 */
public class StepikRenamePsiElementProcessor extends RenamePsiElementProcessor {
    private static final Logger logger = Logger.getInstance(StepikRenamePsiElementProcessor.class);

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return isNotMovableOrRenameElement(element);
    }

    @Override
    public RenameDialog createRenameDialog(
            Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
        return new StepikRenameDialog(project, element, nameSuggestionContext, editor);
    }
}
