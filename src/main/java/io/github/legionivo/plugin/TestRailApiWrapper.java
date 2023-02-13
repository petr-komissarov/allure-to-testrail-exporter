package io.github.legionivo.plugin;

import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiDeclarationStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReturnStatementImpl;
import io.github.legionivo.plugin.api.TestRailClient;
import io.github.legionivo.plugin.api.TestRailClientBuilder;
import io.github.legionivo.plugin.model.Section;
import io.github.legionivo.plugin.model.TestCase;
import io.github.legionivo.plugin.model.TestStep;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static com.intellij.codeInsight.AnnotationUtil.getDeclaredStringAttributeValue;
import static com.intellij.codeInsight.AnnotationUtil.getStringAttributeValue;
import static com.intellij.psi.SyntaxTraverser.psiTraverser;
import static io.github.legionivo.plugin.enums.State.AUTOMATED;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.*;
import static java.util.regex.Matcher.quoteReplacement;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;

class TestRailApiWrapper {

    private static final String VALUE = "value";
    private final Annotations annotations = new Annotations();
    private final Settings settings;
    private final TestRailClient testRailClient;

    TestRailApiWrapper(Settings settings) {
        this.settings = settings;
        if (
                isTestRailAuthSettingsNotConfigured()
                        || isIntSettingNotConfigured(settings.getProjectId())
                        || isIntSettingNotConfigured(settings.getSuiteId())
        ) {
            this.testRailClient = null;
        } else {
            this.testRailClient = new TestRailClientBuilder(
                    settings.getApiUrl(),
                    settings.getUserName(),
                    settings.getPassword()
            ).build();
        }
    }

    public boolean isTestRailClientConfigured() {
        return testRailClient != null;
    }

    private boolean isTestRailAuthSettingsNotConfigured() {
        return isStringSettingNotConfigured(settings.getApiUrl())
                || isStringSettingNotConfigured(settings.getUserName())
                || isStringSettingNotConfigured(settings.getPassword());
    }

    private boolean isStringSettingNotConfigured(String string) {
        return string == null || string.isEmpty() || string.isBlank();
    }

    private boolean isIntSettingNotConfigured(int integer) {
        return integer == 0;
    }

    private List<TestStep> getSteps(final PsiMethod method) {
        List<TestStep> list = new ArrayList<>();
        PsiClass testClass = (PsiClass) method.getParent();
        PsiMethod[] classMethods = testClass.getMethods();

        try (Stream<PsiMethod> methods = stream(classMethods)) {
            methods.filter(classMethod -> isBeforeAllAnnotation(classMethod) || isBeforeEachAnnotation(classMethod))
                    .forEach(classMethod -> addBeforeSteps(classMethod, list));
        }

        PsiCodeBlock block = method.getBody();
        List<PsiMethodCallExpression> methodCallExpressions = psiTraverser().withRoot(block)
                .postOrderDfsTraversal().filter(PsiMethodCallExpression.class).toList();

        try (Stream<PsiMethodCallExpression> methodsCalls = methodCallExpressions.stream()) {
            methodsCalls.map(this::getStepsFromMethod).forEach(list::add);
        }

        return list;
    }

    private void addBeforeSteps(PsiMethod classMethod, List<TestStep> list) {
        final PsiStatement[] statements = ofNullable(classMethod.getBody())
                .map(PsiCodeBlock::getStatements)
                .orElse(new PsiStatement[]{});

        final List<PsiMethodCallExpression> methodCallExpressions = new ArrayList<>();
        for (PsiStatement statement : statements) {
            PsiElement[] children = statement.getChildren();
            addMethodSteps(children, methodCallExpressions);
        }

        for (PsiMethodCallExpression methodCallExpression : methodCallExpressions) {
            list.add(getStepsFromMethod(methodCallExpression));
        }
    }

    private void addMethodSteps(PsiElement[] children, List<PsiMethodCallExpression> methodCallExpressions) {
        try (Stream<PsiElement> elements = stream(children)) {
            elements.filter(PsiMethodCallExpression.class::isInstance).forEach(t -> {
                PsiMethodCallExpression expression = (PsiMethodCallExpression) t;
                if (isStepMethod(requireNonNull(expression.resolveMethod()))) {
                    methodCallExpressions.add(expression);
                }
            });
        }
    }

    private String extractStringFromVarArgs(PsiExpression[] expressions) {
        StringJoiner joiner = new StringJoiner(", ");

        for (PsiExpression psiExpression : expressions) {
            joiner.add(getValueFromExpression(psiExpression));
        }
        return joiner.toString();
    }

    private TestStep getStepsFromMethod(PsiMethodCallExpression methodCallExpression) {
        Map<String, String> expressionsMap = new HashMap<>();
        TestStep step = null;
        PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
        PsiParameter[] methodParameters = requireNonNull(methodCallExpression.resolveMethod()).getParameterList().getParameters();

        if (methodCallExpression.getText().toLowerCase().startsWith("assert")) {
            step = new TestStep().setName(methodCallExpression.getText().replace("\"", "'"));
        } else {
            int counter = 0;
            while (counter < expressions.length) {
                if (!methodParameters[counter].isVarArgs()) {
                    expressionsMap.put(methodParameters[counter].getName(), getValueFromExpression(expressions[counter]));
                    counter++;
                } else {
                    expressionsMap.put(methodParameters[counter].getName(), extractStringFromVarArgs(expressions));
                    counter = expressions.length;
                }
            }

            PsiMethod m = methodCallExpression.resolveMethod();
            if (m != null && isStepMethod(m)) {
                PsiAnnotation annotation = m.getAnnotation(annotations.getAllure2StepAnnotation());
                String stringAttributeValue = getStringAttributeValue(requireNonNull(annotation), VALUE);
                String stepText = processNameTemplate(stringAttributeValue, expressionsMap).replaceAll("`", "\"");
                String stepName = requireNonNull(stepText);
                step = new TestStep().setName(stepName);
            }
        }
        return step;
    }

    private String processNameTemplate(final String template, final Map<String, String> params) {
        final Matcher matcher = compile("\\{([^}]*)}").matcher(template);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher
                    .appendReplacement(sb, quoteReplacement(processPattern(matcher.group(1), params)
                            .orElseGet(matcher::group)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Optional<String> processPattern(final String pattern, final Map<String, String> params) {
        if (pattern.isEmpty()) {
            return empty();
        }
        final String parameterName = pattern.split("\\.")[0];
        if (!params.containsKey(parameterName)) {
            return empty();
        }
        return of(params.get(parameterName));
    }

    private String getValueFromExpression(PsiExpression expression) {
        if (expression != null) {
            if (expression instanceof PsiMethodCallExpression) {
                return getValueFromMethodCallExpression(expression);
            } else if (expression instanceof PsiReferenceExpression) {
                return getValueFromReferenceExpression(expression);
            } else if (expression instanceof PsiLiteralExpression) {
                return expression.getText().replaceAll("^\"|\"$", "");
            } else if (expression instanceof PsiPrefixExpression) {
                return expression.getText();
            } else if (expression instanceof PsiPolyadicExpression) {
                return getValueFromPsiPolyadicExpression((PsiPolyadicExpression) expression);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private String getValueFromMethodCallExpression(PsiExpression expression) {
        PsiMethod method = ((PsiMethodCallExpressionImpl) expression).resolveMethod();
        if (requireNonNull(method).hasAnnotation(annotations.getOwnerKeyAnnotation())
                || method.getAnnotations().length > 0 && !method.hasAnnotation(annotations.getAllure2StepAnnotation())) {
            return getValueFromAnnotatedMethod(expression, method);
        } else if (method.getBody() != null) {
            return getValueFromStatement(method.getBody().getStatements(), expression);
        } else {
            return expression.getText();
        }
    }

    private String getValueFromReferenceExpression(PsiExpression expression) {
        PsiVariable variable = (PsiVariable) ((PsiReferenceExpressionImpl) expression).resolve();
        if (requireNonNull(variable).hasInitializer()) {
            PsiExpression initializer = requireNonNull(variable).getInitializer();
            if (initializer == null) {
                return requireNonNull(variable.getNameIdentifier()).getText();
            } else {
                return requireNonNull(initializer).getText().replace("\"", "");
            }
        } else {
            return expression.getText();
        }
    }

    private String getValueFromStatement(PsiStatement[] statements, PsiExpression expression) {
        if (statements[0] instanceof PsiDeclarationStatementImpl) {
            return null;
        }

        if (((PsiReturnStatementImpl) statements[0]).getReturnValue() instanceof PsiReferenceExpression) {
            PsiExpression psiReferenceExpression = (((PsiReturnStatementImpl) statements[0]).getReturnValue());
            assert psiReferenceExpression != null;
            PsiVariable variable = (PsiVariable) ((PsiReferenceExpressionImpl) psiReferenceExpression).resolve();
            if ((((PsiReturnStatementImpl) statements[0]).getReturnValue() instanceof PsiLiteralExpression)) {
                return (requireNonNull(((PsiReturnStatementImpl) statements[0])
                        .getReturnValue())
                        .getText())
                        .replaceAll("^\"|\"$", "");
            } else if (requireNonNull(variable).hasInitializer()) {
                return getValueFromPsiVariable(variable);
            } else {
                return expression.getText();
            }
        } else {
            return null;
        }
    }

    private String getValueFromPsiVariable(PsiVariable variable) {
        PsiExpression initializer = requireNonNull(variable).getInitializer();
        if (initializer == null) {
            return requireNonNull(variable.getNameIdentifier()).getText();
        } else if (initializer instanceof PsiMethodCallExpressionImpl) {
            PsiMethod psiMethod = ((PsiMethodCallExpressionImpl) initializer).resolveMethod();
            if (requireNonNull(psiMethod).hasAnnotation(annotations.getOwnerKeyAnnotation()) || psiMethod.getAnnotations().length > 0) {
                return getValueFromAnnotatedMethod(initializer, psiMethod);
            } else {
                return null;
            }
        } else {
            return requireNonNull(initializer).getText().replace("\"", "");
        }
    }

    private String getValueFromAnnotatedMethod(PsiExpression expression, PsiMethod method) {
        PsiModifierList modifierList = method.getModifierList();
        Project project = expression.getProject();
        PsiAnnotation[] list = modifierList.getAnnotations();
        PsiLiteralExpression expression1 = (PsiLiteralExpression) list[0].findAttributeValue(VALUE);
        String parameterValue = requireNonNull(expression1).getText().replace("\"", "");
        return PropertiesImplUtil.findPropertiesByKey(project, parameterValue).get(0).getValue();
    }

    private String getValueFromPsiPolyadicExpression(PsiPolyadicExpression polyadicExpression) {
        PsiExpression[] operands = polyadicExpression.getOperands();
        StringBuilder fullExpression = new StringBuilder();

        try (Stream<PsiExpression> expressions = stream(operands)) {
            expressions.forEach(operand -> {
                if (operand instanceof PsiReferenceExpression) {
                    PsiElement element = ((PsiReferenceExpression) operand).resolve();
                    if (element == null) {
                        return;
                    }
                    getValueFromPsiElement(element, fullExpression);
                    return;
                }
                if (operand instanceof PsiLiteralExpression) {
                    fullExpression.append(((PsiLiteralExpression) operand).getValue());
                }
            });
        }

        return fullExpression.toString();
    }

    private void getValueFromPsiElement(PsiElement element, StringBuilder fullExpression) {
        if (element instanceof PsiField) {
            List<PsiReferenceExpression> list = psiTraverser().withRoot(element).filter(PsiReferenceExpression.class).toList();
            if (list.size() > 0) {
                PsiField psiField = ((PsiField) requireNonNull(list.get(0).resolve()));
                fullExpression.append(requireNonNull(psiField.getInitializer()).getText());
            } else {
                PsiExpression psiExpression = ((PsiField) element).getInitializer();
                if (psiExpression == null || psiExpression.getText() == null) {
                    return;
                }
                fullExpression.append(requireNonNull(((PsiField) element).getInitializer()).getText());
            }
        }
    }

    private boolean isStepMethod(final PsiMethod method) {
        return method.hasAnnotation(annotations.getAllure2StepAnnotation());
    }

    private boolean isBeforeEachAnnotation(final PsiMethod method) {
        return method.hasAnnotation(annotations.getJunitBeforeEachAnnotation());
    }

    private boolean isBeforeAllAnnotation(final PsiMethod method) {
        return method.hasAnnotation(annotations.getJunitBeforeAllAnnotation());
    }

    int createSections(String... sections) {
        int parentSectionId = -1;
        for (int i = 0; i < sections.length; i++) {
            if (i == 0) {
                parentSectionId = saveSection(settings.getProjectId(), settings.getSuiteId(), sections[i]).getId();
            } else {
                parentSectionId = saveSection(settings.getProjectId(), settings.getSuiteId(), parentSectionId, sections[i]).getId();
            }
        }
        return parentSectionId;
    }

    private Section saveSection(int projectId, int suiteId, String name) {
        if (sectionExists(projectId, suiteId, name)) {
            return getSection(projectId, suiteId, name);
        }

        Section section = new Section();
        section.setSuiteId(suiteId);
        section.setName(name);

        return createSection(projectId, section);
    }

    private Section saveSection(int projectId, int suiteId, int parentId, String name) {
        if (sectionExists(projectId, suiteId, name)) {
            return getSection(projectId, suiteId, name);
        }

        Section section = new Section();
        section.setSuiteId(suiteId);
        section.setParentId(parentId);
        section.setName(name);

        return createSection(projectId, section);
    }

    private boolean sectionExists(int projectId, int suiteId, String name) {
        try {
            return getSection(projectId, suiteId, name) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private Section getSection(int projectId, int suiteId, String name) {
        try (Stream<Section> sections = testRailClient.getSections(projectId, suiteId).stream()) {
            return sections.filter(it -> it.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No such section"));
        }
    }

    private Section createSection(int projectId, Section section) {
        return testRailClient.addSection(projectId, section);
    }

    TestCase createTestCase(int sectionId, PsiMethod testMethod) {
        TestCase testCase = setTestCaseDetails(testMethod);
        return saveTestCase(sectionId, testCase);
    }

    void updateTestCase(PsiMethod testMethod) {
        TestCase testCase = setTestCaseDetails(testMethod);
        int id = Integer.parseInt(requireNonNull(getStringAttributeValue(
                requireNonNull(testMethod.getAnnotation(annotations.getAllure2TmsLinkAnnotation())), VALUE)));
        updateTestCase(id, testCase);
    }

    private TestCase setTestCaseDetails(PsiMethod testMethod) {
        TestCase testCase = new TestCase();
        if (!settings.isExportOnlyTestNamesCheckBoxEnabled()) {
            testCase.setCustomSteps(toScenario(getSteps(testMethod)));
        }
        return getTestCase(testMethod, testCase);
    }

    @NotNull
    private TestCase getTestCase(PsiMethod testMethod, TestCase testCase) {
        String title;
        if (testMethod.getAnnotation(annotations.getJunitDisplayNameAnnotation()) == null) {
            title = testMethod.getName();
        } else {
            title = getStringAttributeValue(requireNonNull(
                    testMethod.getAnnotation(annotations.getJunitDisplayNameAnnotation())), VALUE);
        }
        testCase.setTitle(title);
        testCase.setCustomState(AUTOMATED.getValue());
        testCase.setRefs(getLinkRef(testMethod));
        testCase.setTypeId(3);
        return testCase;
    }

    private String toScenario(final List<TestStep> steps) {
        try (Stream<TestStep> testSteps = steps.stream()) {
            return testSteps.filter(Objects::nonNull)
                    .map(TestStep::getName)
                    .map(step -> step.replace("{", "\\{"))
                    .map(step -> "# " + step)
                    .collect(joining("\r\n"));
        }
    }

    private TestCase saveTestCase(int sectionId, TestCase testCase) {
        TestCase tCase = getTestCase(sectionId, testCase.getTitle());
        return tCase == null
                ? testRailClient.addTestCase(sectionId, testCase)
                : tCase;
    }

    private void updateTestCase(int caseId, TestCase testCase) {
        testRailClient.updateTestCase(caseId, testCase);
    }

    private String getLinkRef(PsiMethod testMethod) {
        if (testMethod.hasAnnotation(annotations.getAllure2LinkAnnotation()) && testMethod.hasAnnotation(annotations.getAllure2IssueAnnotation())) {
            return getDeclaredStringAttributeValue(requireNonNull(testMethod.getAnnotation(annotations.getAllure2LinkAnnotation())), "name")
                    + ", " + getDeclaredStringAttributeValue(requireNonNull(testMethod.getAnnotation(annotations.getAllure2IssueAnnotation())), VALUE);
        } else if (testMethod.hasAnnotation(annotations.getAllure2LinkAnnotation())) {
            return getDeclaredStringAttributeValue(requireNonNull(testMethod.getAnnotation(annotations.getAllure2LinkAnnotation())), "name");
        } else if (testMethod.hasAnnotation(annotations.getAllure2IssueAnnotation())) {
            return getDeclaredStringAttributeValue(requireNonNull(testMethod.getAnnotation(annotations.getAllure2IssueAnnotation())), VALUE);
        } else {
            return "";
        }
    }

    private List<TestCase> getTestCases(int sectionId) {
        return testRailClient.getTestCases(settings.getProjectId(), settings.getSuiteId(), sectionId);
    }

    private TestCase getTestCase(int sectionId, String testCaseTitle) {
        try (Stream<TestCase> cases = getTestCases(sectionId).stream()) {
            return cases.filter(it -> it.getTitle()
                    .equals(testCaseTitle))
                    .findFirst()
                    .orElse(null);
        }
    }
}


