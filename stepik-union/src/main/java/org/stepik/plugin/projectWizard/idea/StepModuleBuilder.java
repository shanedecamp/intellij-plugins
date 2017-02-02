package org.stepik.plugin.projectWizard.idea;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.tmp.learning.StepikProjectManager;
import com.jetbrains.tmp.learning.SupportedLanguages;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.courseFormat.StepFile;
import com.jetbrains.tmp.learning.courseFormat.StepNode;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

class StepModuleBuilder extends ModuleBuilderWithSrc {
    private static final Logger logger = Logger.getInstance(StepModuleBuilder.class);
    private final StepNode stepNode;
    private final Project project;

    StepModuleBuilder(String moduleDir, @NotNull StepNode stepNode, @NotNull Project project) {
        this.stepNode = stepNode;
        this.project = project;
        String stepName = stepNode.getDirectory();
        setName(stepName);
        setModuleFilePath(FileUtil.join(moduleDir, stepName,
                stepName + ModuleFileType.DOT_DEFAULT_EXTENSION));
    }

    @NotNull
    @Override
    public Module createModule(@NotNull ModifiableModuleModel moduleModel) throws InvalidDataException,
            IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
        Module module = super.createModule(moduleModel);
        createStepContent();

        return module;
    }

    private void createStepContent() {
        StepikProjectManager stepManager = StepikProjectManager.getInstance(project);
        stepNode.setCurrentLang(stepManager.getDefaultLang());

        String src = stepManager.getProject().getBasePath() + String.join("/", stepNode.getPath(), EduNames.SRC);
        createMainFile(stepNode, src);
    }

    private void createMainFile(@NotNull StepNode stepNode, @NotNull String src) {
        SupportedLanguages currentLang = stepNode.getCurrentLang();
        String name = currentLang.getMainFileName();
        final StepFile stepFile = stepNode.getStepFiles().get(name);
        final String text = stepFile.getText();
        final File file = new File(src, name);

        try {
            FileUtil.writeToFile(file, text);
        } catch (IOException e) {
            logger.error("Failed create main file: " + file);
        }
    }
}
