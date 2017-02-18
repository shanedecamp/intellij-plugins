package com.jetbrains.tmp.learning.courseFormat;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.jetbrains.tmp.learning.SupportedLanguages;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.stepik.StepikConnectorLogin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.api.client.StepikApiClient;
import org.stepik.api.exceptions.StepikClientException;
import org.stepik.api.objects.lessons.CompoundUnitLesson;
import org.stepik.api.objects.steps.BlockView;
import org.stepik.api.objects.steps.BlockViewOptions;
import org.stepik.api.objects.steps.Limit;
import org.stepik.api.objects.steps.Sample;
import org.stepik.api.objects.steps.Step;
import org.stepik.api.objects.steps.Steps;
import org.stepik.api.objects.units.Units;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.tmp.learning.SupportedLanguages.INVALID;
import static com.jetbrains.tmp.learning.stepik.StepikConnectorLogin.authAndGetStepikApiClient;

public class StepNode extends Node<Step, StepNode, Step, StepNode> {
    private static final Logger logger = Logger.getInstance(StepNode.class);
    private StudyStatus status;
    private List<SupportedLanguages> supportedLanguages;
    private SupportedLanguages currentLang;
    private long courseId;

    public StepNode() {}

    public StepNode(@NotNull Step data, @Nullable ProgressIndicator indicator) {
        super(data, indicator);
    }

    public void init(@Nullable StudyNode parent, boolean isRestarted, @Nullable ProgressIndicator indicator) {
        if (indicator != null) {
            indicator.setText("Refresh a step: " + getName());
            indicator.setText2("");
        }

        supportedLanguages = null;
        courseId = 0;

        if (isRestarted) {
            status = StudyStatus.UNCHECKED;
        }

        super.init(parent, isRestarted, indicator);
    }

    @Override
    protected void loadData(long id) {
        try {
            StepikApiClient stepikApiClient = StepikConnectorLogin.authAndGetStepikApiClient();
            Steps steps = stepikApiClient.steps()
                    .get()
                    .id(id)
                    .execute();

            Step step;
            if (!steps.isEmpty()) {
                step = steps.getSteps().get(0);
            } else {
                step = new Step();
                step.setId(id);
            }
            setData(step);
        } catch (StepikClientException logged) {
            logger.warn(String.format("Failed step lesson data id=%d", id), logged);
        }
    }

    @Override
    protected Class<StepNode> getChildClass() {
        return StepNode.class;
    }

    @NotNull
    public String getText() {
        Step data = getData();
        return data != null ? data.getBlock().getText() : "";
    }

    @NotNull
    public String getTemplate(@NotNull SupportedLanguages language) {
        Map<String, String> templates;
        Step data = getData();
        if (data == null) {
            return "";
        }
        templates = getData().getBlock().getOptions().getCodeTemplates();
        return templates.getOrDefault(language.getName(), "");
    }

    @NotNull
    public String getCurrentTemplate() {
        return getTemplate(getCurrentLang());
    }

    @Override
    public List<StepNode> getChildren() {
        return Collections.emptyList();
    }

    @Override
    protected List<Step> getChildDataList() {
        return Collections.emptyList();
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

        Step data = getData();
        if (data == null) {
            return 0;
        }

        int lessonId = data.getLesson();
        if (lessonId == 0) {
            return 0;
        }

        try {
            StepikApiClient stepikApiClient = authAndGetStepikApiClient();

            Units units = stepikApiClient.units()
                    .get()
                    .lesson(lessonId)
                    .execute();
            if (units.isEmpty()) {
                return 0;
            }

            LessonNode lessonNode = new LessonNode();
            CompoundUnitLesson lessonData = lessonNode.getData();
            if (lessonData != null) {
                lessonData.setUnit(units.getItems().get(0));
            }

            courseId = lessonNode.getCourseId();
            return courseId;
        } catch (StepikClientException ignored) {
        }
        return 0;
    }

    @Override
    @NotNull
    public StudyStatus getStatus() {
        if (status == null) {
            status = StudyStatus.UNCHECKED;
        }
        return status;
    }

    public void setStatus(@Nullable StudyStatus status) {
        this.status = status;
    }

    @NotNull
    private Map<String, Limit> getLimits() {
        Step data = getData();
        if (data == null) {
            return Collections.emptyMap();
        }
        return data.getBlock().getOptions().getLimits();
    }

    @NotNull
    public Limit getLimit() {
        return getLimits().getOrDefault(getCurrentLang().getName(), new Limit());
    }

    @NotNull
    public List<SupportedLanguages> getSupportedLanguages() {
        if (supportedLanguages == null) {
            supportedLanguages = new ArrayList<>();

            BlockView block;
            Step data = getData();
            if (data == null) {
                return supportedLanguages;
            }

            block = data.getBlock();

            if (getType() == StepType.CODE) {
                BlockViewOptions options = block.getOptions();

                Map<String, String> templates = options.getCodeTemplates();
                templates.keySet().forEach(key -> {
                    SupportedLanguages language = SupportedLanguages.langOfName(key);

                    if (language != INVALID && !supportedLanguages.contains(language)) {
                        supportedLanguages.add(language);
                    }
                });
            }
        }
        return supportedLanguages;
    }

    @NotNull
    public SupportedLanguages getCurrentLang() {
        List<SupportedLanguages> languages = getSupportedLanguages();
        if (currentLang == null || currentLang == INVALID || !languages.contains(currentLang)) {
            currentLang = getFirstSupportLang();
        }
        return currentLang;
    }

    public void setCurrentLang(@Nullable SupportedLanguages currentLang) {
        this.currentLang = currentLang;
    }

    @NotNull
    private SupportedLanguages getFirstSupportLang() {
        List<SupportedLanguages> languages = getSupportedLanguages();
        if (languages.isEmpty()) {
            return INVALID;
        } else {
            return languages.get(0);
        }
    }

    @Override
    protected Class<Step> getDataClass() {
        return Step.class;
    }

    @NotNull
    public List<Sample> getSamples() {
        Step data = getData();
        if (data != null && getType() == StepType.CODE) {
            return data.getBlock().getOptions().getSamples();
        }

        return Collections.emptyList();
    }

    @NotNull
    public StepType getType() {
        Step data = getData();
        if (data == null) {
            return StepType.UNKNOWN;
        }
        return StepType.of(data.getBlock().getName());
    }

    public boolean isStepFile(@NotNull String fileName) {
        return (EduNames.SRC + "/" + getCurrentLang().getMainFileName()).equals(fileName);
    }

    @NotNull
    @Override
    String getDirectoryPrefix() {
        return EduNames.STEP;
    }

    @Override
    public boolean canBeLeaf() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StepNode stepNode = (StepNode) o;

        if (status != stepNode.status) return false;
        //noinspection SimplifiableIfStatement
        if (currentLang != stepNode.currentLang) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (currentLang != null ? currentLang.hashCode() : 0);
        result = 31 * result + super.hashCode();
        return result;
    }

    @NotNull
    public VideoStepNodeHelper asVideoStep() {
        return new VideoStepNodeHelper(this);
    }

    public ChoiceStepNodeHelper asChoiceStep() {
        return new ChoiceStepNodeHelper(this);
    }
}
