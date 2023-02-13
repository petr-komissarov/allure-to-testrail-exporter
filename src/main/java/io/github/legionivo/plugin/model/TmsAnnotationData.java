package io.github.legionivo.plugin.model;

import com.intellij.psi.PsiMethod;

public class TmsAnnotationData {
    private TestCase testCase;
    private PsiMethod psiMethod;

    public TestCase getTestCase() {
        return testCase;
    }

    public void setTestCase(TestCase testCase) {
        this.testCase = testCase;
    }

    public PsiMethod getPsiMethod() {
        return psiMethod;
    }

    public void setPsiMethod(PsiMethod psiMethod) {
        this.psiMethod = psiMethod;
    }
}
