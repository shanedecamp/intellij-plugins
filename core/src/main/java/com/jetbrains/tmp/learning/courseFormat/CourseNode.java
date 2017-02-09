package com.jetbrains.tmp.learning.courseFormat;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stepik.api.client.StepikApiClient;
import org.stepik.api.exceptions.StepikClientException;
import org.stepik.api.objects.courses.Course;
import org.stepik.api.objects.sections.Section;
import org.stepik.api.objects.sections.Sections;
import org.stepik.api.objects.users.User;
import org.stepik.api.objects.users.Users;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.tmp.learning.stepik.StepikConnectorLogin.authAndGetStepikApiClient;

public class CourseNode extends Node<SectionNode> {
    private static final Logger logger = Logger.getInstance(CourseNode.class);
    private Course data;
    private List<User> authors;
    private List<SectionNode> sectionNodes;

    public CourseNode() {
    }

    public CourseNode(Course data, ProgressIndicator indicator) {
        this.data = data;
        init(true, indicator);
    }

    public void init(boolean isRestarted, @Nullable ProgressIndicator indicator) {
        try {
            StepikApiClient stepikApiClient = authAndGetStepikApiClient();
            if (indicator != null) {
                indicator.setText("Refresh " + getName());
                indicator.setText2("Update sections");
            }

            authors = null;

            List<Long> sectionsIds = getData().getSections();
            if (sectionsIds.size() > 0) {
                Sections sections = stepikApiClient.sections()
                        .get()
                        .id(sectionsIds)
                        .execute();

                for (Section section : sections.getSections()) {
                    SectionNode sectionNode = getChildById(section.getId());
                    if (sectionNode != null) {
                        sectionNode.setData(section);
                    } else {
                        SectionNode item = new SectionNode(this, section);
                        if (item.getLessonNodes().size() > 0) {
                            getSectionNodes().add(item);
                        }
                    }
                }

                clearMapNodes();
                sortChildren();
            }
        } catch (StepikClientException logged) {
            logger.warn("A course initialization don't is fully", logged);
        }

        for (SectionNode sectionNode : getSectionNodes()) {
            sectionNode.init(this, isRestarted, indicator);
        }
    }

    @SuppressWarnings("unused")
    @NotNull
    @Transient
    public List<User> getAuthors() {
        if (authors == null) {
            List<Long> authorsIds = data.getAuthors();
            if (authorsIds.size() > 0) {
                try {
                    Users users = authAndGetStepikApiClient().users()
                            .get()
                            .id(authorsIds)
                            .execute();
                    authors = users.getUsers();
                } catch (StepikClientException e) {
                    return Collections.emptyList();
                }
            }
        }
        return authors != null ? authors : Collections.emptyList();
    }

    @Transient
    @NotNull
    @Override
    public String getName() {
        return getData().getTitle();
    }

    @Transient
    @Override
    public int getPosition() {
        return 0;
    }

    @Transient
    @Override
    public long getId() {
        return getData().getId();
    }

    @Override
    public long getCourseId() {
        return getId();
    }

    @NotNull
    public List<SectionNode> getSectionNodes() {
        if (sectionNodes == null) {
            sectionNodes = new ArrayList<>();
        }
        return sectionNodes;
    }

    @SuppressWarnings("unused")
    public void setSectionNodes(@Nullable List<SectionNode> sectionNodes) {
        this.sectionNodes = sectionNodes;
        sortChildren();
        clearMapNodes();
    }

    @Transient
    @NotNull
    @Override
    public StudyStatus getStatus() {
        for (SectionNode sectionNode : getSectionNodes()) {
            if (sectionNode.getStatus() != StudyStatus.SOLVED)
                return StudyStatus.UNCHECKED;
        }

        return StudyStatus.SOLVED;
    }

    @Override
    public List<SectionNode> getChildren() {
        return getSectionNodes();
    }

    @SuppressWarnings("WeakerAccess")
    @NotNull
    public Course getData() {
        if (data == null) {
            data = new Course();
        }
        return data;
    }

    @SuppressWarnings("unused")
    public void setData(@Nullable Course data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CourseNode that = (CourseNode) o;

        return data != null ? data.equals(that.data) : that.data == null;
    }

    @Override
    public int hashCode() {
        return data != null ? data.hashCode() : 0;
    }
}
