package io.github.legionivo.plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications.Bus;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import io.github.legionivo.plugin.model.TestCase;
import io.github.legionivo.plugin.model.TmsAnnotationData;
import io.github.legionivo.plugin.util.PsiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.intellij.codeInsight.AnnotationUtil.getDeclaredStringAttributeValue;
import static com.intellij.notification.NotificationType.*;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.PSI_ELEMENT;
import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static io.github.legionivo.plugin.Settings.getInstance;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

public class TestRailAction extends AnAction {
    private static final String TEST_RAIL_ACTION = "TestRail.Action";
    private static final String NOTIFIER_SKIPPED_MESSAGE = "Export skipped";
    private static final Object LOCKER = new Object();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        SwingWorker<LinkedList<TmsAnnotationData>, Integer> worker = new SwingWorker<>() {
            @Override
            protected LinkedList<TmsAnnotationData> doInBackground() {
                synchronized (LOCKER) {
                    LinkedList<TmsAnnotationData> results = new LinkedList<>();
                    try {
                        getApplication().runReadAction(() -> {
                            final PsiElement element = e.getData(PSI_ELEMENT);
                            if (element instanceof PsiClass) {
                                try (Stream<PsiMethod> methods = stream(((PsiClass) element).getMethods())) {
                                    methods.forEach(m -> tryToExportTestMethodToTR(m, results));
                                }
                            } else if (element instanceof PsiMethod) {
                                tryToExportTestMethodToTR(element, results);
                            } else {
                                Bus.notify(new Notification(TEST_RAIL_ACTION,
                                        NOTIFIER_SKIPPED_MESSAGE,
                                        "You can export TestClass or TestMethod only",
                                        WARNING));
                            }
                        });
                    } catch (Exception e) {
                        Bus.notify(new Notification(TEST_RAIL_ACTION,
                                "Error occurred during the export",
                                e.getMessage(),
                                WARNING));
                    }

                    return results;
                }
            }

            @Override
            protected void done() {
                try {
                    LinkedList<TmsAnnotationData> getResults = get();
                    ApplicationManager.getApplication().invokeLaterOnWriteThread(
                            () -> getResults.forEach(r -> createCaseIdAnnotation(r.getTestCase(), r.getPsiMethod())),
                            ModalityState.defaultModalityState());
                } catch (InterruptedException | ExecutionException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
    }

    private void tryToExportTestMethodToTR(PsiElement element, LinkedList<TmsAnnotationData> results) {
        Annotations annotations = new Annotations();
        PsiMethod method = (PsiMethod) element;
        if (!(method.hasAnnotation(annotations.getJunitTestAnnotation()) || method.hasAnnotation(annotations.getJunitParameterizedTestAnnotation()))) {
            Bus.notify(new Notification(TEST_RAIL_ACTION,
                    NOTIFIER_SKIPPED_MESSAGE,
                    "Method hasn't Junit @Test annotation",
                    WARNING));
            return;
        }

        TestRailApiWrapper testRail = new TestRailApiWrapper(requireNonNull(getInstance(method.getProject())));
        if (!testRail.isTestRailClientConfigured()) {
            Bus.notify(new Notification(TEST_RAIL_ACTION,
                    NOTIFIER_SKIPPED_MESSAGE,
                    "TestRail configuration not fully completed",
                    WARNING));
            return;
        }

        PsiClass testClass = (PsiClass) method.getParent();
        PsiAnnotation featureClassAnnotation = testClass.getAnnotation(annotations.getAllure2FeatureAnnotation());
        PsiAnnotation featureMethodAnnotation = method.getAnnotation(annotations.getAllure2FeatureAnnotation());
        String featureName;
        if (featureMethodAnnotation != null) {
            featureName = getDeclaredStringAttributeValue(requireNonNull(featureMethodAnnotation), "value");
        } else if (featureClassAnnotation != null) {
            featureName = getDeclaredStringAttributeValue(requireNonNull(featureClassAnnotation), "value");
        } else {
            Bus.notify(new Notification(TEST_RAIL_ACTION,
                    "Export to failed",
                    "You should add @Feature annotation for TestClass or TestMethod",
                    ERROR));
            return;
        }
        if (method.hasAnnotation(annotations.getAllure2TmsLinkAnnotation())) {
            testRail.updateTestCase(method);
        } else {
            int sectionId = testRail.createSections(featureName);
            TestCase testCase = testRail.createTestCase(sectionId, method);
            TmsAnnotationData tmsAnnotationData = new TmsAnnotationData();
            tmsAnnotationData.setTestCase(testCase);
            tmsAnnotationData.setPsiMethod(method);
            results.add(tmsAnnotationData);
        }
        Bus.notify(new Notification(TEST_RAIL_ACTION,
                "Export to TestRail",
                "Finished exporting [" + method.getName() + "]",
                INFORMATION));
    }

    private void createCaseIdAnnotation(TestCase testCase, PsiMethod method) {
        Annotations annotations = new Annotations();
        PsiUtils psiUtils = new PsiUtils();
        final PsiAnnotation annotation = psiUtils.createAnnotation(getCaseAnnotationText(testCase.getId()), method);
        final Project project = method.getProject();

        PsiAnnotation displayNameAnnotation = method.getAnnotation(annotations.getJunitDisplayNameAnnotation());

        CommandProcessor.getInstance().executeCommand(project, () -> getApplication().runWriteAction(() -> {
            PsiModifierList modifierList = method.getModifierList();
            if (modifierList.hasAnnotation(annotations.getAllure2TmsLinkAnnotation())) {
                Bus.notify(new Notification(TEST_RAIL_ACTION,
                        "@TmsLink annotation creation skipped",
                        "Test method already has @TmsLink annotation",
                        INFORMATION));
                return;
            }
            psiUtils.addImport(method.getContainingFile(), annotations.getAllure2TmsLinkAnnotation());
            if (displayNameAnnotation == null) {
                modifierList.add(annotation);

            } else {
                modifierList.addBefore(annotation, displayNameAnnotation);
            }
        }), "Insert TestRail Id", null);
    }

    private String getCaseAnnotationText(int id) {
        return format("@%s(\"%s\")", new Annotations().getAllure2TmsLinkAnnotation(), id);
    }
}
