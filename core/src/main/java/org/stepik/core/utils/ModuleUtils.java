package org.stepik.core.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.jetbrains.tmp.learning.courseFormat.StepNode;
import com.jetbrains.tmp.learning.courseFormat.StudyNode;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.stepik.plugin.projectWizard.idea.StepModuleBuilder;

import java.io.IOException;

/**
 * @author meanmail
 */
public class ModuleUtils {
    private static final Logger logger = Logger.getInstance(ModuleUtils.class);

    public static void createStepModule(
            @NotNull Project project,
            @NotNull StepNode step,
            @NotNull ModifiableModuleModel moduleModel) {
        StudyNode lesson = step.getParent();
        if (lesson != null) {
            String moduleDir = String.join("/", project.getBasePath(), lesson.getPath());
            StepModuleBuilder stepModuleBuilder = new StepModuleBuilder(moduleDir, step, project);
            try {
                stepModuleBuilder.createModule(moduleModel);
            } catch (IOException | ModuleWithNameAlreadyExists | JDOMException | ConfigurationException e) {
                logger.warn("Cannot create step: " + step.getDirectory(), e);
            }
        }
    }
}