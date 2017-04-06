package org.stepik.core.courseFormat;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.api.client.StepikApiClient;
import org.stepik.api.exceptions.StepikClientException;
import org.stepik.api.objects.lessons.CompoundUnitLesson;
import org.stepik.api.objects.lessons.Lesson;
import org.stepik.api.objects.lessons.Lessons;
import org.stepik.api.objects.sections.Sections;
import org.stepik.api.objects.steps.Step;
import org.stepik.api.objects.steps.Steps;
import org.stepik.core.core.EduNames;
import org.stepik.core.stepik.StepikConnectorLogin;

import java.util.Collections;
import java.util.List;

import static org.stepik.core.stepik.StepikConnectorLogin.authAndGetStepikApiClient;

public class LessonNode extends Node<CompoundUnitLesson, StepNode, Step, StepNode> {
    private static final Logger logger = Logger.getInstance(LessonNode.class);
    private long courseId;

    public LessonNode() {
    }

    public LessonNode(@NotNull Project project, @NotNull CompoundUnitLesson data) {
        super(project, data);
    }

    @Override
    protected List<Step> getChildDataList() {
        Steps steps = new Steps();
        try {
            StepikApiClient stepikApiClient = StepikConnectorLogin.getStepikApiClient();

            CompoundUnitLesson data = getData();
            List<Long> stepsIds = data != null ? data.getLesson().getSteps() : Collections.emptyList();

            if (!stepsIds.isEmpty()) {
                steps = stepikApiClient.steps()
                        .get()
                        .id(stepsIds)
                        .execute();
            }
        } catch (StepikClientException logged) {
            logger.warn("A lesson initialization don't is fully", logged);
        }

        return steps.getSteps();
    }

    @Override
    public void init(@NotNull Project project, @Nullable StudyNode parent) {
        courseId = 0;
        super.init(project, parent);
    }

    @Override
    protected boolean loadData(long id) {
        try {
            CompoundUnitLesson data = getData();
            if (data == null) {
                return true;
            }

            StepikApiClient stepikApiClient = StepikConnectorLogin.authAndGetStepikApiClient();
            Lessons lessons = stepikApiClient.lessons()
                    .get()
                    .id(id)
                    .execute();

            Lesson lesson;
            if (!lessons.isEmpty()) {
                lesson = lessons.getLessons().get(0);
                data.setLesson(lesson);
            } else {
                lesson = new Lesson();
                lesson.setId(id);
            }
            data.setLesson(lesson);

            CompoundUnitLesson oldData = this.getData();
            return oldData == null || !oldData.getUpdateDate().equals(data.getUpdateDate());
        } catch (StepikClientException logged) {
            logger.warn(String.format("Failed load lesson data id=%d", id), logged);
        }
        return true;
    }


    @Override
    protected Class<StepNode> getChildClass() {
        return StepNode.class;
    }

    @Override
    protected Class<Step> getChildDataClass() {
        return Step.class;
    }

    @Override
    public long getCourseId() {
        StudyNode parent = getParent();
        if (parent != null) {
            return parent.getCourseId();
        }

        if (courseId != 0) {
            return courseId;
        }

        CompoundUnitLesson data = getData();
        int sectionId = data != null ? data.getUnit().getSection() : 0;
        if (sectionId == 0) {
            return 0;
        }

        try {
            StepikApiClient stepikApiClient = authAndGetStepikApiClient();

            Sections sections = stepikApiClient.sections()
                    .get()
                    .id(sectionId)
                    .execute();
            if (sections.isEmpty()) {
                return 0;
            }
            courseId = sections.getItems().get(0).getCourse();
            return courseId;
        } catch (StepikClientException ignored) {
        }
        return 0;
    }

    @Override
    protected Class<CompoundUnitLesson> getDataClass() {
        return CompoundUnitLesson.class;
    }

    @NotNull
    @Override
    String getDirectoryPrefix() {
        return EduNames.LESSON;
    }
}
