package com.jetbrains.tmp.learning;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.tmp.learning.core.EduNames;
import com.jetbrains.tmp.learning.core.EduUtils;
import com.jetbrains.tmp.learning.courseFormat.Course;
import com.jetbrains.tmp.learning.courseFormat.StudyStatus;
import com.jetbrains.tmp.learning.courseFormat.TaskFile;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StudySerializationUtils {

    private static final String PLACEHOLDERS = "placeholders";
    private static final String LINE = "line";
    private static final String START = "start";
    private static final String OFFSET = "offset";
    private static final String TEXT = "text";
    private static final String LESSONS = "lessons";
    private static final String COURSE = "course";
    private static final String COURSE_TITLED = "Course";
    private static final String STATUS = "status";
    private static final String AUTHOR = "author";
    private static final String AUTHORS = "authors";
    private static final String MY_INITIAL_START = "myInitialStart";

    private StudySerializationUtils() {
    }

    public static class StudyUnrecognizedFormatException extends Exception {}

    public static class Xml {
        private static final Logger logger = Logger.getInstance(StudySerializationUtils.class);
        public final static String COURSE_ELEMENT = "courseElement";
        public final static String MAIN_ELEMENT = "StepikStudyTaskManager";
        public static final String MAP = "map";
        public static final String KEY = "key";
        public static final String VALUE = "value";
        public static final String NAME = "name";
        public static final String LIST = "list";
        public static final String OPTION = "option";
        public static final String INDEX = "index";
        public static final String STUDY_STATUS_MAP = "myStudyStatusMap";
        public static final String TASK_STATUS_MAP = "myTaskStatusMap";
        public static final String LENGTH = "length";
        public static final String ANSWER_PLACEHOLDERS = "answerPlaceholders";
        public static final String TASK_LIST = "taskList";
        public static final String TASK_FILES = "taskFiles";
        public static final String INITIAL_STATE = "initialState";
        public static final String MY_INITIAL_STATE = "MyInitialState";
        public static final String MY_LINE = "myLine";
        public static final String MY_START = "myStart";
        public static final String MY_LENGTH = "myLength";
        public static final String HINTS = "hints";
        public static final String HINT = "hint";
        public static final String AUTHOR_TITLED = "Author";
        public static final String FIRST_NAME = "first_name";
        public static final String SECOND_NAME = "second_name";
        public static final String MY_INITIAL_LINE = "myInitialLine";
        public static final String MY_INITIAL_LENGTH = "myInitialLength";
        public static final String ANSWER_PLACEHOLDER = "AnswerPlaceholder";
        public static final String TASK_WINDOWS = "taskWindows";
        public static final String RESOURCE_PATH = "resourcePath";
        public static final String COURSE_DIRECTORY = "courseDirectory";
        public static final String SECTIONS = "sections";
        public static final String SECTION_TITLED = "Section";
        public static final String SECTIONS_NAMES = "sectionsNames";

        private Xml() {
        }

        public static int getVersion(Element element) throws StudyUnrecognizedFormatException {
            if (element.getChild(COURSE_ELEMENT) != null) {
                return 1;
            }

            final Element taskManager = element.getChild(MAIN_ELEMENT);

            Element versionElement = getChildWithName(taskManager, "VERSION");
            if (versionElement == null) {
                return -1;
            }

            return Integer.valueOf(versionElement.getAttributeValue(VALUE));
        }

        public static Element convertToSecondVersion(Element element) throws StudyUnrecognizedFormatException {
            final Element oldCourseElement = element.getChild(COURSE_ELEMENT);
            Element state = new Element(MAIN_ELEMENT);

            Element course = addChildWithName(state, COURSE, oldCourseElement.clone());
            course.setName(COURSE_TITLED);

            Element author = getChildWithName(course, AUTHOR);
            String authorString = author.getAttributeValue(VALUE);
            course.removeContent(author);

            String[] names = authorString.split(" ", 2);
            Element authorElement = new Element(AUTHOR_TITLED);
            addChildWithName(authorElement, FIRST_NAME, names[0]);
            addChildWithName(authorElement, SECOND_NAME, names.length == 1 ? "" : names[1]);

            addChildList(course, AUTHORS, Collections.singletonList(authorElement));

            Element courseDirectoryElement = getChildWithName(course, RESOURCE_PATH);
            renameElement(courseDirectoryElement, COURSE_DIRECTORY);

            for (Element lesson : getChildList(course, LESSONS)) {
                incrementIndex(lesson);
                for (Element task : getChildList(lesson, TASK_LIST)) {
                    incrementIndex(task);
                    Map<String, Element> taskFiles = getChildMap(task, TASK_FILES);
                    for (Element taskFile : taskFiles.values()) {
                        renameElement(getChildWithName(taskFile, TASK_WINDOWS), ANSWER_PLACEHOLDERS);
                        for (Element placeholder : getChildList(taskFile, ANSWER_PLACEHOLDERS)) {
                            placeholder.setName(ANSWER_PLACEHOLDER);

                            Element initialState = new Element(MY_INITIAL_STATE);
                            addChildWithName(placeholder, INITIAL_STATE, initialState);
                            addChildWithName(initialState,
                                    MY_LINE,
                                    getChildWithName(placeholder, MY_INITIAL_LINE).getAttributeValue(VALUE));
                            addChildWithName(initialState,
                                    MY_START,
                                    getChildWithName(placeholder, MY_INITIAL_START).getAttributeValue(VALUE));
                            addChildWithName(initialState,
                                    MY_LENGTH,
                                    getChildWithName(placeholder, MY_INITIAL_LENGTH).getAttributeValue(VALUE));
                        }
                    }

                }
            }
            element.removeContent();
            element.addContent(state);
            return element;
        }

        public static Map<String, String> fillStatusMap(
                Element taskManagerElement,
                String mapName,
                XMLOutputter outputter)
                throws StudyUnrecognizedFormatException {
            Map<Element, String> sourceMap = getChildMap(taskManagerElement, mapName);
            Map<String, String> destMap = new HashMap<>();
            for (Map.Entry<Element, String> entry : sourceMap.entrySet()) {
                String status = entry.getValue();
                if (StudyStatus.of(status) == StudyStatus.UNCHECKED) {
                    continue;
                }
                destMap.put(outputter.outputString(entry.getKey()), status);
            }
            return destMap;
        }

        public static Element convertToThirdVersion(
                Element state,
                Project project) throws StudyUnrecognizedFormatException {
            Element taskManagerElement = state.getChild(MAIN_ELEMENT);
            XMLOutputter outputter = new XMLOutputter();

            Map<String, String> placeholderTextToStatus = fillStatusMap(taskManagerElement,
                    STUDY_STATUS_MAP,
                    outputter);
            Map<String, String> taskFileToStatusMap = fillStatusMap(taskManagerElement, TASK_STATUS_MAP, outputter);

            Element courseElement = getChildWithName(taskManagerElement, COURSE).getChild(COURSE_TITLED);
            for (Element lesson : getChildList(courseElement, LESSONS)) {
                int lessonIndex = getAsInt(lesson, INDEX);
                for (Element task : getChildList(lesson, TASK_LIST)) {
                    String taskStatus = null;
                    int taskIndex = getAsInt(task, INDEX);
                    Map<String, Element> taskFiles = getChildMap(task, TASK_FILES);
                    for (Map.Entry<String, Element> entry : taskFiles.entrySet()) {
                        Element taskFileElement = entry.getValue();
                        String taskFileText = outputter.outputString(taskFileElement);
                        String taskFileStatus = taskFileToStatusMap.get(taskFileText);
                        if (taskFileStatus != null && (taskStatus == null || StudyStatus.of(taskFileStatus) == StudyStatus.FAILED)) {
                            taskStatus = taskFileStatus;
                        }
                        Document document = StudyUtils.getDocument(project.getBasePath(),
                                lessonIndex,
                                taskIndex,
                                entry.getKey());
                        if (document == null) {
                            continue;
                        }
                        for (Element placeholder : getChildList(taskFileElement, ANSWER_PLACEHOLDERS)) {
                            taskStatus = addStatus(outputter, placeholderTextToStatus, taskStatus, placeholder);
                            addOffset(document, placeholder);
                            addInitialState(document, placeholder);
                            addHints(placeholder);
                        }
                    }
                    if (taskStatus != null) {
                        addChildWithName(task, STATUS, taskStatus);
                    }
                }
            }
            return state;
        }

        public static String addStatus(
                XMLOutputter outputter,
                Map<String, String> placeholderTextToStatus,
                String taskStatus,
                Element placeholder) {
            String placeholderText = outputter.outputString(placeholder);
            String status = placeholderTextToStatus.get(placeholderText);
            if (status != null) {
                addChildWithName(placeholder, STATUS, status);
                if (taskStatus == null || StudyStatus.of(status) == StudyStatus.FAILED) {
                    taskStatus = status;
                }
            }
            return taskStatus;
        }

        public static void addInitialState(
                Document document,
                Element placeholder) throws StudyUnrecognizedFormatException {
            Element initialState = getChildWithName(placeholder, INITIAL_STATE).getChild(MY_INITIAL_STATE);
            int initialLine = getAsInt(initialState, MY_LINE);
            int initialStart = getAsInt(initialState, MY_START);
            int initialOffset = document.getLineStartOffset(initialLine) + initialStart;
            addChildWithName(initialState, OFFSET, initialOffset);
            renameElement(getChildWithName(initialState, MY_LENGTH), LENGTH);
        }

        public static void addOffset(Document document, Element placeholder) throws StudyUnrecognizedFormatException {
            int line = getAsInt(placeholder, LINE);
            int start = getAsInt(placeholder, START);
            int offset = document.getLineStartOffset(line) + start;
            addChildWithName(placeholder, OFFSET, offset);
        }

        public static void addHints(@NotNull Element placeholder) throws StudyUnrecognizedFormatException {
            final String hint = getChildWithName(placeholder, HINT).getAttribute(VALUE).getValue();
            Element listElement = new Element(LIST);
            final Element hintElement = new Element(OPTION);
            hintElement.setAttribute(VALUE, hint);
            listElement.setContent(hintElement);
            addChildWithName(placeholder, HINTS, listElement);
        }

        public static int getAsInt(Element element, String name) throws StudyUnrecognizedFormatException {
            return Integer.valueOf(getChildWithName(element, name).getAttributeValue(VALUE));
        }

        public static void incrementIndex(Element element) throws StudyUnrecognizedFormatException {
            Element index = getChildWithName(element, INDEX);
            int indexValue = Integer.parseInt(index.getAttributeValue(VALUE));
            changeValue(index, indexValue + 1);
        }

        public static void renameElement(Element element, String newName) {
            element.setAttribute(NAME, newName);
        }

        public static void changeValue(Element element, Object newValue) {
            element.setAttribute(VALUE, newValue.toString());
        }

        public static Element addChildWithName(Element parent, String name, Element value) {
            Element child = new Element(OPTION);
            child.setAttribute(NAME, name);
            child.addContent(value);
            parent.addContent(child);
            return value;
        }

        public static Element addChildWithName(Element parent, String name, Object value) {
            Element child = new Element(OPTION);
            child.setAttribute(NAME, name);
            child.setAttribute(VALUE, value.toString());
            parent.addContent(child);
            return child;
        }

        public static Element addChildList(Element parent, String name, List<Element> elements) {
            Element listElement = new Element(LIST);
            elements.forEach(listElement::addContent);
            return addChildWithName(parent, name, listElement);
        }

        public static List<Element> getChildList(Element parent, String name) throws StudyUnrecognizedFormatException {
            Element listParent = getChildWithName(parent, name);
            if (listParent != null) {
                Element list = listParent.getChild(LIST);
                if (list != null) {
                    return list.getChildren();
                }
            }
            return Collections.emptyList();
        }

        @NotNull
        public static Set<Element> getChildSet(Element parent, String name) {
            Element listParent = getChildWithNameOrNull(parent, name);
            if (listParent != null) {
                Element list = listParent.getChild("set");
                if (list != null) {
                    return new HashSet<>(list.getChildren());
                }
            }
            return Collections.emptySet();
        }

        public static Element getChildWithName(Element parent, String name) throws StudyUnrecognizedFormatException {
            Element child = getChildWithNameOrNull(parent, name);
            if (child != null) {
                return child;
            } else {
                StudyUnrecognizedFormatException e = new StudyUnrecognizedFormatException();
                logger.warn(e);
                throw e;
            }
        }

        public static Element getChildWithNameOrNull(Element parent, String name) {
            for (Element child : parent.getChildren()) {
                Attribute attribute = child.getAttribute(NAME);
                if (attribute == null) {
                    continue;
                }
                if (name.equals(attribute.getValue())) {
                    return child;
                }
            }
            return null;
        }

        public static <K, V> Map<K, V> getChildMap(
                Element element,
                String name) throws StudyUnrecognizedFormatException {
            Element mapParent = getChildWithName(element, name);
            if (mapParent != null) {
                Element map = mapParent.getChild(MAP);
                if (map != null) {
                    HashMap result = new HashMap();
                    for (Element entry : map.getChildren()) {
                        K key = entry.getAttribute(KEY) == null ?
                                (K) entry.getChild(KEY).getChildren().get(0) :
                                (K) entry.getAttributeValue(KEY);
                        V value = entry.getAttribute(VALUE) == null ?
                                (V) entry.getChild(VALUE).getChildren().get(0) :
                                (V) entry.getAttributeValue(VALUE);
                        result.put(key, value);
                    }
                    return result;
                }
            }
            return Collections.emptyMap();
        }

        static Element convertToForthVersion(Element state, Project project)
                throws StudyUnrecognizedFormatException {
            Element taskManagerElement = state.getChild(MAIN_ELEMENT);

            Element courseElement = getChildWithName(taskManagerElement, COURSE).getChild(COURSE_TITLED);
            if (courseElement == null) {
                return state;
            }

            List<Element> lessons = getChildList(courseElement, LESSONS);
            Map<String, Element> lessonsNames = new java.util.HashMap<>();
            for (Element lesson : lessons) {
                int index = getAsInt(lesson, INDEX);
                lessonsNames.put(EduNames.LESSON + index, lesson.clone());
            }
            Map<String, String> sectionsNames = getChildMap(courseElement, SECTIONS_NAMES);

            ArrayList<Element> list = new ArrayList<>();
            sectionsNames.entrySet().forEach(entry -> {
                Element section = new Element(SECTION_TITLED);
                int index = EduUtils.getIndex(entry.getKey(), EduNames.SECTION);
                addChildWithName(section, INDEX, index);
                addChildWithName(section, NAME, entry.getValue());
                ArrayList<Element> lessonsList = new ArrayList<>();
                VirtualFile sectionDir = project.getBaseDir().findChild(EduNames.SECTION + index);
                if (sectionDir != null) {
                    for (VirtualFile child : sectionDir.getChildren()) {
                        String name = child.getName();
                        Element lesson = lessonsNames.get(name);
                        if (lesson != null) {
                            lessonsList.add(lesson);
                        }
                    }
                }
                addChildList(section, LESSONS, lessonsList);
                list.add(section);
            });

            addChildList(courseElement, SECTIONS, list);
            if (courseElement.removeContent(getChildWithName(courseElement, "lessons")))
                logger.info("lessons was removed from STM.xml");
            if (courseElement.removeContent(getChildWithName(courseElement, "sectionsNames")))
                logger.info("sectionsNames was removed from STM.xml");

            return state;
        }

        static Element convertToFifthVersion(Element state, Project project)
                throws StudyUnrecognizedFormatException {
            Element taskManagerElement = state.getChild(MAIN_ELEMENT);

            Element langManager = getChildWithName(taskManagerElement, "langManager").getChild("LangManager");
            Map<String, Element> langSettingsMap = getChildMap(langManager, "langSettingsMap");
            Map<String, Pair<String, Set<String>>> mapIdLangSetting = new java.util.HashMap<>();
            langSettingsMap.entrySet().forEach(element -> {
                        Element langSetting = element.getValue();

                        Element currentLangElement = getChildWithNameOrNull(langSetting, "currentLang");
                        String currentLang = currentLangElement == null ?
                                "" :
                                currentLangElement.getAttribute("value").getValue();

                        Set<Element> supportLangs = getChildSet(langSetting, "supportLangs");
                        Set<String> taskLangs = new HashSet<>();

                        supportLangs.forEach(lang -> taskLangs.add(lang.getAttribute("value").getValue()));

                        Pair<String, Set<String>> ls = Pair.create(currentLang, taskLangs);
                        mapIdLangSetting.put(element.getKey(), ls);
                    }
            );

            Element courseElement = getChildWithName(taskManagerElement, COURSE).getChild(COURSE_TITLED);
            if (courseElement == null) {
                logger.info("courseElement is null");
                return state;
            }

            List<Element> sections = getChildList(courseElement, SECTIONS);
            for (Element section : sections) {
                List<Element> lessons = getChildList(section, LESSONS);
                for (Element lesson : lessons) {
                    List<Element> taskList = getChildList(lesson, TASK_LIST);
                    for (Element task : taskList) {
                        String stepId = getChildWithName(task, "stepId").getAttribute("value").getValue();
                        Pair<String, Set<String>> ls = mapIdLangSetting.get(stepId);
                        if (ls != null) {
                            Element list = new Element("list");
                            ls.second.forEach(suppLang -> {
                                Element child = new Element(OPTION);
                                child.setAttribute(VALUE, suppLang);
                                list.addContent(child);
                            });
                            addChildWithName(task, "currentLang", ls.first);
                            addChildWithName(task, "supportedLanguages", list);
                        } else {
                            logger.warn(String.format("step with id :%s is not found", stepId));
                        }
                    }
                }
            }

            if (taskManagerElement.removeContent(getChildWithName(taskManagerElement, "langManager"))) {
                logger.info("LangManager was removed from STM.xml");
            }
            return state;
        }
    }

    public static class Json {

        public static final String TASK_LIST = "task_list";
        public static final String TASK_FILES = "task_files";

        private Json() {
        }

        public static class CourseTypeAdapter implements JsonDeserializer<Course> {

            private final File myCourseFile;

            public CourseTypeAdapter(File courseFile) {
                myCourseFile = courseFile;
            }

            @Override
            public Course deserialize(
                    JsonElement json,
                    Type typeOfT,
                    JsonDeserializationContext context) throws JsonParseException {
                return new GsonBuilder().create().fromJson(json, Course.class);
            }
        }

        @Deprecated
        public static class StepikTaskFileAdapter implements JsonDeserializer<TaskFile> {

            @Override
            public TaskFile deserialize(
                    JsonElement json,
                    Type typeOfT,
                    JsonDeserializationContext context) throws JsonParseException {
                JsonObject taskFileObject = json.getAsJsonObject();
                JsonArray placeholders = taskFileObject.getAsJsonArray(PLACEHOLDERS);
                for (JsonElement placeholder : placeholders) {
                    JsonObject placeholderObject = placeholder.getAsJsonObject();
                    int line = placeholderObject.getAsJsonPrimitive(LINE).getAsInt();
                    int start = placeholderObject.getAsJsonPrimitive(START).getAsInt();
                    if (line == -1) {
                        placeholderObject.addProperty(OFFSET, start);
                    } else {
                        Document document = EditorFactory.getInstance()
                                .createDocument(taskFileObject.getAsJsonPrimitive(TEXT).getAsString());
                        placeholderObject.addProperty(OFFSET, document.getLineStartOffset(line) + start);
                    }
                }
                return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                        .fromJson(json, TaskFile.class);
            }
        }

        public static class SupportedLanguagesSerializer implements JsonSerializer<SupportedLanguages>
        {
            @Override
            public JsonElement serialize(SupportedLanguages src, Type typeOfSrc, JsonSerializationContext context) {
                return new JsonPrimitive(src.toString());
            }
        }

        public static class SupportedLanguagesDeserializer implements JsonDeserializer<SupportedLanguages> {
            private final Logger logger = Logger.getInstance(SupportedLanguagesDeserializer.class);

            @Override
            public SupportedLanguages deserialize(
                    JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                String data = json.getAsString();
                logger.info("json = " + data);

                switch (data) {
                    case ("java8") : return SupportedLanguages.JAVA;
                    case ("python3") : return SupportedLanguages.PYTHON;
                    default: return SupportedLanguages.INVALID;
                }
            }
        }
    }
}
