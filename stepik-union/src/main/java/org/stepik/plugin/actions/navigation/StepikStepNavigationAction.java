package org.stepik.plugin.actions.navigation;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.courseFormat.StepNode;
import com.jetbrains.tmp.learning.courseFormat.StudyNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.core.metrics.Metrics;

import javax.swing.*;

import static org.stepik.core.metrics.MetricsStatus.SUCCESSFUL;

abstract class StepikStepNavigationAction extends StudyStepNavigationAction {
    StepikStepNavigationAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
        super(text, description, icon);
    }

    @Override
    public void navigateStep(@NotNull final Project project) {
        StudyNode currentNode = StudyUtils.getSelectedStep(project);
        StudyNode targetNode;

        targetNode = getTargetStep(currentNode);

        if (targetNode == null) {
            return;
        }
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            FileEditorManager.getInstance(project).closeFile(file);
        }

        VirtualFile projectDir = project.getBaseDir();
        if (projectDir == null) {
            return;
        }

        VirtualFile mainFile;
        if (targetNode instanceof StepNode) {
            VirtualFile srcDir = projectDir.findFileByRelativePath(targetNode.getPath() + "/" + EduNames.SRC);
            if (srcDir == null) {
                return;
            }

            StepNode stepNode = (StepNode) targetNode;

            mainFile = srcDir.findChild(stepNode.getCurrentLang().getMainFileName());
            if (mainFile == null) {
                mainFile = srcDir;
            }
        } else {
            mainFile = projectDir.findFileByRelativePath(targetNode.getPath());
        }

        updateProjectView(project, mainFile);
        Metrics.navigateAction(project, targetNode, SUCCESSFUL);

        ToolWindow runToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN);
        if (runToolWindow != null) {
            runToolWindow.hide(null);
        }
    }
}
