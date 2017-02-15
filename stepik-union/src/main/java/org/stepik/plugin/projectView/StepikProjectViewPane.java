package org.stepik.plugin.projectView;

import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.project.Project;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.courseFormat.StudyNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author meanmail
 */
public class StepikProjectViewPane extends ProjectViewPane {
    protected StepikProjectViewPane(@NotNull Project project) {
        super(project);
    }

    @Override
    public String getTitle() {
        return "Stepik project";
    }

    @NotNull
    @Override
    public String getId() {
        return "StepikProjectPane";
    }

    @Override
    public int getWeight() {
        return -1;
    }

    @Override
    public JComponent createComponent() {
        JComponent component = super.createComponent();

        getTree().addTreeSelectionListener(e -> {
            StudyNode studyNode = StudyUtils.getSelectedNodeInTree(myProject);
            StudyUtils.setStudyNode(myProject, studyNode);
        });

        return component;
    }
}
