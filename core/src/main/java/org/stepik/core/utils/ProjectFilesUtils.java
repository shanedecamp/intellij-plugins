package org.stepik.core.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.courseFormat.StepNode;
import com.jetbrains.tmp.learning.courseFormat.StudyNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static org.stepik.core.utils.ProductGroup.IDEA;

/**
 * @author meanmail
 */
public class ProjectFilesUtils {

    public static final String SEPARATOR = "/";
    private static final char SEPARATOR_CHAR = '/';
    private static final String SECTION_EXPR = "(section[0-9]+)";
    private static final String LESSON_PATH_EXPR = "(section[0-9]+/lesson[0-9]+|lesson[0-9]+)";
    private static final String STEP_PATH_EXPR = "(section[0-9]+/lesson[0-9]+/step[0-9]+|lesson[0-9]+/step[0-9]+|step[0-9]+)";
    private static final String SRC_PATH_EXPR = "(" + STEP_PATH_EXPR + SEPARATOR + EduNames.SRC + "|" + EduNames.SRC + ")";
    private static final String COURSE_DIRECTORIES = "\\.|" + SECTION_EXPR + "|" + LESSON_PATH_EXPR + "|" + STEP_PATH_EXPR + "|" + SRC_PATH_EXPR;
    private static final String HIDE_PATH_EXPR = SRC_PATH_EXPR + SEPARATOR + EduNames.HIDE;

    public static boolean isCanNotBeTarget(@NotNull String targetPath) {
        //noinspection SimplifiableIfStatement
        if (isHideDir(targetPath) || isWithinHideDir(targetPath)) {
            return true;
        }
        return !(isWithinSrc(targetPath) || isWithinSandbox(targetPath) || isSandbox(targetPath) || isSrc(targetPath));
    }

    private static boolean isStepFile(@NotNull StudyNode root, @NotNull String path) {
        StudyNode studyNode = StudyUtils.getStudyNode(root, path);

        if (studyNode instanceof StepNode) {
            String filename = getRelativePath(studyNode.getPath(), path);
            return ((StepNode) studyNode).isStepFile(filename);
        }
        return false;
    }

    public static boolean isNotMovableOrRenameElement(@NotNull StudyNode node, @NotNull String path) {
        if (isWithinSrc(path)) {
            return isHideDir(path) || isWithinHideDir(path) || isStepFile(node, path);
        }

        return !isWithinSandbox(path);
    }

    public static boolean isSandbox(@NotNull String path) {
        return path.matches(EduNames.SANDBOX_DIR);
    }

    private static boolean isSrc(@NotNull String path) {
        return path.matches(SRC_PATH_EXPR);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isWithinSandbox(@NotNull String path) {
        return path.matches(EduNames.SANDBOX_DIR + SEPARATOR + ".*");
    }

    static boolean isWithinSrc(@NotNull String path) {
        return path.matches(SRC_PATH_EXPR + SEPARATOR + ".*");
    }

    @NotNull
    public static String getRelativePath(@NotNull String basePath, @NotNull String path) {
        String relativePath = FileUtil.getRelativePath(basePath, path, SEPARATOR_CHAR);
        return relativePath == null ? path : relativePath;
    }

    static boolean isStudyItemDir(@NotNull String relativePath) {
        return relativePath.matches(COURSE_DIRECTORIES);
    }

    @NotNull
    private static String[] splitPath(@NotNull String path) {
        return path.split(SEPARATOR);
    }

    static boolean isWithinHideDir(@NotNull String path) {
        return path.matches(HIDE_PATH_EXPR + SEPARATOR + ".*");
    }

    static boolean isHideDir(@NotNull String path) {
        return path.matches(HIDE_PATH_EXPR);
    }

    @Nullable
    static String getParent(@NotNull String path) {
        String[] dirs = splitPath(path);
        if (dirs.length == 0 || path.isEmpty() || path.equals(".")) {
            return null;
        } else if (dirs.length == 1) {
            return ".";
        }

        StringBuilder parentPath = new StringBuilder(dirs[0]);

        for (int i = 1; i < dirs.length - 1; i++) {
            parentPath.append(SEPARATOR).append(dirs[i]);
        }

        return parentPath.toString();
    }

    @Nullable
    public static VirtualFile getOrCreateSrcDirectory(@NotNull Project project, StepNode stepNode) {
        VirtualFile baseDir = project.getBaseDir();
        String srcPath = stepNode.getPath() + "/" + EduNames.SRC;
        VirtualFile srcDirectory = baseDir.findFileByRelativePath(srcPath);
        if (srcDirectory == null) {
            srcDirectory = getOrCreateDirectory(baseDir, srcPath);
            if (srcDirectory != null && PluginUtils.isCurrent(IDEA)) {
                ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();
                ApplicationManager.getApplication().runWriteAction(() -> {
                    ModuleUtils.createStepModule(project, stepNode, model);
                    model.commit();
                });
                VirtualFileManager.getInstance().syncRefresh();
            }
        }
        return srcDirectory;
    }

    @Nullable
    static PsiDirectory getOrCreateSrcPsiDirectory(@NotNull Project project, @NotNull StepNode stepNode) {
        VirtualFile directory = getOrCreateSrcDirectory(project, stepNode);
        if (directory == null) {
            return null;
        }
        return PsiManager.getInstance(project).findDirectory(directory);
    }

    @Nullable
    private static VirtualFile getOrCreateDirectory(@NotNull VirtualFile baseDir, @NotNull String directoryPath) {
        VirtualFile srcDir = baseDir.findFileByRelativePath(directoryPath);
        if (srcDir == null) {
            srcDir = ApplicationManager.getApplication().runWriteAction((Computable<VirtualFile>) () -> {
                VirtualFile dir;
                try {
                    String[] paths = directoryPath.split("/");
                    dir = baseDir;
                    for (String path : paths) {
                        VirtualFile child = dir.findChild(path);
                        if (child == null) {
                            dir = dir.createChildDirectory(null, path);
                        } else {
                            dir = child;
                        }
                    }
                } catch (IOException e) {
                    return null;
                }

                return dir;
            });
        }
        return srcDir;
    }

    static PsiDirectory getOrCreatePsiDirectory(@NotNull Project project, @NotNull String directoryPath) {
        VirtualFile baseDir = project.getBaseDir();
        VirtualFile directory = getOrCreateDirectory(baseDir, directoryPath);
        if (directory == null) {
            return null;
        }
        return PsiManager.getInstance(project).findDirectory(directory);
    }
}
