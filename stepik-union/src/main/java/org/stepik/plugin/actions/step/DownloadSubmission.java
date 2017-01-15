package org.stepik.plugin.actions.step;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.SupportedLanguages;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.courseFormat.CourseNode;
import com.jetbrains.tmp.learning.courseFormat.StepNode;
import com.jetbrains.tmp.learning.courseFormat.StudyStatus;
import com.jetbrains.tmp.learning.stepik.StepikConnectorLogin;
import icons.AllStepikIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.api.client.StepikApiClient;
import org.stepik.api.objects.submissions.Submission;
import org.stepik.api.objects.submissions.Submissions;
import org.stepik.api.queries.Order;

import javax.swing.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * @author meanmail
 * @since 0.8
 */
public class DownloadSubmission extends AbstractStepAction {
    private static final String ACTION_ID = "STEPIK.DownloadSubmission";
    private static final String SHORTCUT = "ctrl alt pressed PAGE_DOWN";

    public DownloadSubmission() {
        super("Download submission from the List(" + KeymapUtil.getShortcutText(
                new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")",
                "Download submission from the List", AllStepikIcons.ToolWindow.download);
    }

    @NotNull
    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Nullable
    @Override
    public String[] getShortcuts() {
        return new String[]{SHORTCUT};
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        downloadSubmission(e.getProject());
    }

    private void downloadSubmission(@Nullable Project project) {
        if (project == null) {
            return;
        }

        StepNode stepNode = StudyUtils.getSelectedStep(project);
        if (stepNode == null) {
            return;
        }

        List<Submission> submissions = getSubmissions(stepNode);

        if (submissions == null) {
            return;
        }

        SupportedLanguages currentLang = stepNode.getCurrentLang();

        submissions = filterSubmissions(submissions, currentLang);

        showPopup(project, stepNode, submissions, currentLang);
    }

    @Nullable
    private List<Submission> getSubmissions(@NotNull StepNode stepNode) {
        long stepId = stepNode.getId();
        long userId = StepikConnectorLogin.getCurrentUser().getId();

        StepikApiClient stepikApiClient = StepikConnectorLogin.getStepikApiClient();

        Submissions submissions = stepikApiClient.submissions()
                .get()
                .step(stepId)
                .user(userId)
                .order(Order.DESC)
                .execute();

        return submissions.getSubmissions();
    }

    @NotNull
    private List<Submission> filterSubmissions(
            @NotNull List<Submission> submissions,
            @NotNull SupportedLanguages currentLang) {
        final String langName = currentLang.getName();
        return submissions.stream()
                .filter(submission -> submission.getReply().getLanguage().startsWith(langName))
                .collect(Collectors.toList());
    }

    private void showPopup(
            @NotNull Project project,
            @NotNull StepNode stepNode,
            @NotNull List<Submission> submissions,
            @NotNull SupportedLanguages currentLang) {
        JBPopupFactory popupFactory = JBPopupFactory.getInstance();

        PopupChooserBuilder builder;
        if (submissions.size() > 0) {
            JList<SubmissionDecorator> list;

            List<SubmissionDecorator> submissionDecorators = submissions.stream()
                    .map(SubmissionDecorator::new).collect(Collectors.toList());
            list = new JList<>(submissionDecorators.toArray(new SubmissionDecorator[submissionDecorators.size()]));
            builder = popupFactory.createListPopupBuilder(list)
                    .addListener(new Listener(list, project, stepNode, currentLang));
        } else {
            JList<String> emptyList = new JList<>(new String[]{"Empty"});
            builder = popupFactory.createListPopupBuilder(emptyList);
        }

        builder = builder.setTitle("Choose submission");

        JBPopup popup = builder.createPopup();

        popup.showCenteredInCurrentWindow(project);
    }

    private void loadSubmission(
            @NotNull Project project,
            @NotNull SupportedLanguages currentLang,
            @NotNull StepNode stepNode,
            @NotNull Submission submission) {

        String fileName = currentLang.getMainFileName();

        String mainFilePath = String.join("/", stepNode.getPath(), EduNames.SRC, fileName);
        VirtualFile mainFile = project.getBaseDir().findFileByRelativePath(mainFilePath);
        if (mainFile == null) {
            return;
        }

        final String finalCode = submission.getReply().getCode();

        StepikApiClient stepikApiClient = StepikConnectorLogin.getStepikApiClient();
        CourseNode courseNode = stepNode.getCourse();
        stepikApiClient.metrics()
                .post()
                .name("ide_plugin")
                .tags("name", "S_Union")
                .tags("action", "download")
                .data("courseId", courseNode != null ? courseNode.getId() : 0)
                .data("stepId", stepNode.getId())
                .execute();

        CommandProcessor.getInstance().executeCommand(project,
                () -> ApplicationManager.getApplication().runWriteAction(
                        () -> {
                            FileDocumentManager documentManager = FileDocumentManager.getInstance();
                            Document document = documentManager.getDocument(mainFile);

                            if (document != null) {
                                document.setText(finalCode);
                                stepNode.setStatus(StudyStatus.of(submission.getStatus()));
                                FileEditorManager.getInstance(project).openFile(mainFile, true);
                                ProjectView.getInstance(project).refresh();
                            }
                        }),
                "Download submission",
                "Download submission");
    }

    private static class SubmissionDecorator {
        private final static SimpleDateFormat timeISOFormat = getTimeISOFormat();
        private final static SimpleDateFormat timeOutFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
        private final Submission submission;

        SubmissionDecorator(Submission submission) {
            this.submission = submission;
        }

        private static SimpleDateFormat getTimeISOFormat() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            TimeZone tz = TimeZone.getTimeZone("UTC");
            format.setTimeZone(tz);
            return format;
        }

        @Override
        public String toString() {
            String localTime;
            String time = submission.getTime();
            try {
                Date utcTime = timeISOFormat.parse(time);
                localTime = timeOutFormat.format(utcTime);
            } catch (ParseException e) {
                localTime = time;
            }

            return String.format("#%d %-7s %s", submission.getId(), submission.getStatus(), localTime);
        }

        Submission getSubmission() {
            return submission;
        }
    }

    private class Listener implements JBPopupListener {
        private final JList<SubmissionDecorator> list;
        private final Project project;
        private final StepNode stepNode;
        private final SupportedLanguages currentLang;

        Listener(
                @NotNull JList<SubmissionDecorator> list,
                @NotNull Project project,
                @NotNull StepNode stepNode,
                @NotNull SupportedLanguages currentLang) {
            this.list = list;
            this.project = project;
            this.stepNode = stepNode;
            this.currentLang = currentLang;
        }

        @Override
        public void beforeShown(LightweightWindowEvent event) {
        }

        @Override
        public void onClosed(LightweightWindowEvent event) {
            if (!event.isOk() || list.isSelectionEmpty()) {
                return;
            }

            Submission submission = list.getSelectedValue().getSubmission();

            if (submission == null) {
                return;
            }

            loadSubmission(project, currentLang, stepNode, submission);
        }
    }
}