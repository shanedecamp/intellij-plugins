package org.stepik.plugin.projectWizard.idea;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.stepik.core.StepikProjectManager;
import org.stepik.core.courseFormat.StepNode;
import org.stepik.core.courseFormat.StudyNode;
import org.stepik.plugin.projectWizard.StepikProjectGenerator;

import java.io.IOException;

import static org.stepik.core.projectWizard.ProjectWizardUtils.createSubDirectories;
import static org.stepik.core.utils.ProjectFilesUtils.getOrCreateSrcDirectory;

public class CourseModuleBuilder extends AbstractModuleBuilder {
    private static final Logger logger = Logger.getInstance(CourseModuleBuilder.class);
    private final StepikProjectGenerator generator = StepikProjectGenerator.getInstance();
    private JavaWizardStep wizardStep;

    @NotNull
    @Override
    public Module createModule(@NotNull ModifiableModuleModel moduleModel)
            throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
        Module baseModule = super.createModule(moduleModel);
        Project project = baseModule.getProject();
        logger.info("Create project module");
        createCourseFromGenerator(moduleModel, project);
        return baseModule;
    }

    private void createCourseFromGenerator(
            @NotNull ModifiableModuleModel moduleModel,
            @NotNull Project project)
            throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
        generator.generateProject(project);

        String moduleDir = getModuleFileDirectory();
        if (moduleDir == null) {
            return;
        }

        logger.info("Module dir = " + moduleDir);
        new SandboxModuleBuilder(moduleDir).createModule(moduleModel);

        StudyNode root = StepikProjectManager.getProjectRoot(project);
        if (root == null) {
            logger.info("Failed to generate builders: project root is null");
            return;
        }

        if (root instanceof StepNode) {
            getOrCreateSrcDirectory(project, (StepNode) root, true, moduleModel);
        } else {
            createSubDirectories(project, generator.getDefaultLang(), root, moduleModel);
            VirtualFileManager.getInstance().syncRefresh();
        }
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(
            @NotNull WizardContext wizardContext,
            @NotNull ModulesProvider modulesProvider) {
        ModuleWizardStep[] previousWizardSteps = super.createWizardSteps(wizardContext, modulesProvider);
        ModuleWizardStep[] wizardSteps = new ModuleWizardStep[previousWizardSteps.length + 1];

        Project project = wizardContext.getProject() == null ?
                DefaultProjectFactory.getInstance().getDefaultProject() :
                wizardContext.getProject();

        wizardStep = new JavaWizardStep(generator, project);
        wizardSteps[0] = wizardStep;

        return wizardSteps;
    }

    JavaWizardStep getWizardStep() {
        return wizardStep;
    }
}