/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.tmp.learning.ui;

import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.ui.JBUI;
import com.jetbrains.tmp.learning.*;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.courseFormat.*;
import com.jetbrains.tmp.learning.stepik.StepikAdaptiveReactionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public abstract class StudyToolWindow extends SimpleToolWindowPanel implements DataProvider, Disposable {
    private static final Logger logger = Logger.getInstance(StudyToolWindow.class);
    private static final String TASK_INFO_ID = "taskInfo";
    private static final String EMPTY_TASK_TEXT = "Please, open any task to see task description";

    private final JBCardLayout myCardLayout;
    private final JPanel myContentPanel;
    private final OnePixelSplitter mySplitPane;
    private JLabel myStatisticLabel;
    private StudyProgressBar myStudyProgressBar;

    public StudyToolWindow() {
        super(true, true);
        myCardLayout = new JBCardLayout();
        myContentPanel = new JPanel(myCardLayout);
        mySplitPane = new OnePixelSplitter(myVertical = true);
    }

    public void init(@NotNull final Project project, final boolean isToolwindow) {
        String taskText = StudyUtils.getTaskText(project);
        if (taskText == null) {
            logger.warn("task text is empty");
            return;
        }

        final DefaultActionGroup group = getActionGroup(project);
        setActionToolbar(group);

        final JPanel panel = new JPanel(new BorderLayout());
        final Course course = StudyTaskManager.getInstance(project).getCourse();
        if (isToolwindow && course != null && course.isAdaptive()) {
            panel.add(new StepikAdaptiveReactionsPanel(project), BorderLayout.NORTH);
        }

        JComponent taskInfoPanel = createTaskInfoPanel(project);
        panel.add(taskInfoPanel, BorderLayout.CENTER);

        final JPanel courseProgress = createCourseProgress(project);
        if (isToolwindow && course != null && !course.isAdaptive() && EduNames.STUDY.equals(course.getCourseMode())) {
            panel.add(courseProgress, BorderLayout.SOUTH);
        }

        myContentPanel.add(TASK_INFO_ID, panel);
        mySplitPane.setFirstComponent(myContentPanel);
        addAdditionalPanels(project);
        myCardLayout.show(myContentPanel, TASK_INFO_ID);

        setContent(mySplitPane);

        if (isToolwindow) {
            StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
            if (configurator != null) {
                final FileEditorManagerListener listener = configurator.getFileEditorManagerListener(project, this);
                project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
            }

            if (StudyTaskManager.getInstance(project).isTurnEditingMode() ||
                    StudyTaskManager.getInstance(project).getToolWindowMode() == StudyToolWindowMode.EDITING) {
                TaskFile file = StudyUtils.getSelectedTaskFile(project);
                if (file != null) {
                    VirtualFile taskDir = file.getTask().getTaskDir(project);
                    setTaskText(taskText, taskDir, project);
                }
            } else {
                setTaskText(taskText, null, project);
            }
        }
    }

    public void setTopComponent(@NotNull final JComponent component) {
        mySplitPane.setFirstComponent(component);
    }

    public void setDefaultTopComponent() {
        mySplitPane.setFirstComponent(myContentPanel);
    }

    public void setActionToolbar(DefaultActionGroup group) {
        JPanel toolbarPanel = createToolbarPanel(group);
        setToolbar(toolbarPanel);
    }

    private void addAdditionalPanels(Project project) {
        StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
        if (configurator != null) {
            Map<String, JPanel> panels = configurator.getAdditionalPanels(project);
            for (Map.Entry<String, JPanel> entry : panels.entrySet()) {
                myContentPanel.add(entry.getKey(), entry.getValue());
            }
        }
    }


    public void dispose() {
    }

    //used in checkiO plugin.
    @SuppressWarnings("unused")
    public void showPanelById(@NotNull final String panelId) {
        myCardLayout.swipe(myContentPanel, panelId, JBCardLayout.SwipeDirection.AUTO);
    }

    //used in checkiO plugin.
    @SuppressWarnings("unused")
    public void setBottomComponent(JComponent component) {
        mySplitPane.setSecondComponent(component);
    }

    //used in checkiO plugin.
    @SuppressWarnings("unused")
    public JComponent getBottomComponent() {
        return mySplitPane.getSecondComponent();
    }

    //used in checkiO plugin.
    @SuppressWarnings("unused")
    public void setTopComponentPrefferedSize(@NotNull final Dimension dimension) {
        myContentPanel.setPreferredSize(dimension);
    }

    //used in checkiO plugin.
    @SuppressWarnings("unused")
    public JPanel getContentPanel() {
        return myContentPanel;
    }


    public abstract JComponent createTaskInfoPanel(Project project);

    public static JPanel createToolbarPanel(ActionGroup group) {
        final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Study", group, true);
        return JBUI.Panels.simplePanel(actionToolBar.getComponent());
    }

    public static DefaultActionGroup getActionGroup(@NotNull final Project project) {
        DefaultActionGroup group = new DefaultActionGroup();
        Course course = StudyTaskManager.getInstance(project).getCourse();
        if (course == null) {
            logger.warn("Course is null");
            return group;
        }
        StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
        if (configurator != null) {
            group.addAll(configurator.getActionGroup(project));
            addAdditionalActions(group);
            return group;
        } else {
            logger.warn("No configurator is provided for plugin");
            return StudyBasePluginConfigurator.getDefaultActionGroup();
        }
    }

    private static void addAdditionalActions(DefaultActionGroup group) {
        StudyActionsProvider[] providers = Extensions.getExtensions(StudyActionsProvider.EP_NAME);
        for (StudyActionsProvider provider : providers) {
            group.addAll(provider.getActions());
        }
    }

    public void setTaskText(String text, VirtualFile taskDirectory, Project project) {
        if (StudyTaskManager.getInstance(project).isTurnEditingMode()) {
            if (taskDirectory == null) {
                logger.info("Failed to enter editing mode for StudyToolWindow");
                return;
            }
            VirtualFile taskTextFile = StudyUtils.findTaskDescriptionVirtualFile(taskDirectory);
            enterEditingMode(taskTextFile, project);
            StudyTaskManager.getInstance(project).setTurnEditingMode(false);
        } else {
            setText(text);
        }
    }

    protected abstract void setText(@NotNull String text);

    public void setEmptyText(@NotNull Project project) {
        if (StudyTaskManager.getInstance(project).getToolWindowMode() == StudyToolWindowMode.EDITING) {
            mySplitPane.setFirstComponent(myContentPanel);
            StudyTaskManager.getInstance(project).setTurnEditingMode(true);
        }
        setTaskText(EMPTY_TASK_TEXT, null, project);
    }

    public enum StudyToolWindowMode {
        TEXT, EDITING
    }


    public void enterEditingMode(VirtualFile taskFile, Project project) {
        final EditorFactory factory = EditorFactory.getInstance();
        Document document = FileDocumentManager.getInstance().getDocument(taskFile);
        if (document == null) {
            return;
        }
        WebBrowserManager.getInstance().setShowBrowserHover(false);
        final EditorEx createdEditor = (EditorEx) factory.createEditor(document, project, taskFile, false);
        Disposer.register(project, new Disposable() {
            public void dispose() {
                factory.releaseEditor(createdEditor);
            }
        });
        JComponent editorComponent = createdEditor.getComponent();
        mySplitPane.setFirstComponent(editorComponent);
        mySplitPane.repaint();

        StudyTaskManager.getInstance(project).setToolWindowMode(StudyToolWindowMode.EDITING);
    }


    public void leaveEditingMode(Project project) {
        WebBrowserManager.getInstance().setShowBrowserHover(true);
        mySplitPane.setFirstComponent(myContentPanel);
        StudyTaskManager.getInstance(project).setToolWindowMode(StudyToolWindowMode.TEXT);
        StudyUtils.updateToolWindows(project);
    }

    private JPanel createCourseProgress(@NotNull final Project project) {
        JPanel contentPanel = new JPanel();
        contentPanel.setBackground(JBColor.WHITE);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
        contentPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        myStudyProgressBar = new StudyProgressBar(0, 20, 10);

        myStatisticLabel = new JLabel("", SwingConstants.LEFT);
        contentPanel.add(myStatisticLabel);
        contentPanel.add(myStudyProgressBar);

        contentPanel.setPreferredSize(new Dimension(100, 60));
        contentPanel.setMinimumSize(new Dimension(300, 40));
        updateCourseProgress(project);
        return contentPanel;
    }

    public void updateCourseProgress(@NotNull final Project project) {
        final Course course = StudyTaskManager.getInstance(project).getCourse();
        if (course != null) {
            int taskNum = 0;
            int taskSolved = 0;
            List<Lesson> lessons = course.getLessons();
            for (Lesson lesson : lessons) {
                if (lesson.getName().equals(EduNames.PYCHARM_ADDITIONAL)) continue;
                taskNum += lesson.getTaskList().size();
                taskSolved += getSolvedTasks(lesson);
            }
            String completedTasks = String.format("%d of %d tasks completed", taskSolved, taskNum);
            double percent = (taskSolved * 100.0) / taskNum;

            myStatisticLabel.setText(completedTasks);
            myStudyProgressBar.setFraction(percent / 100);
        }
    }

    private static int getSolvedTasks(@NotNull final Lesson lesson) {
        int solved = 0;
        for (Task task : lesson.getTaskList()) {
            if (task.getStatus() == StudyStatus.Solved) {
                solved += 1;
            }
        }
        return solved;
    }
}