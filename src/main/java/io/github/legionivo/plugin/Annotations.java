package io.github.legionivo.plugin;

public class Annotations {

    private static final String ALLURE2_STEP_ANNOTATION = "io.qameta.allure.Step";
    private static final String ALLURE2_FEATURE_ANNOTATION = "io.qameta.allure.Feature";
    private static final String ALLURE2_LINK_ANNOTATION = "io.qameta.allure.Link";
    private static final String ALLURE2_TMS_LINK_ANNOTATION = "io.qameta.allure.TmsLink";
    private static final String ALLURE2_ISSUE_ANNOTATION = "io.qameta.allure.Issue";
    private static final String OWNER_KEY_ANNOTATION = "org.aeonbits.owner.Config.Key";

    private static final String JUNIT_TEST_ANNOTATION = "org.junit.jupiter.api.Test";
    private static final String JUNIT_PARAMETERIZED_TEST_ANNOTATION = "org.junit.jupiter.params.ParameterizedTest";
    private static final String JUNIT_BEFORE_ALL_ANNOTATION = "org.junit.jupiter.api.BeforeAll";
    private static final String JUNIT_BEFORE_EACH_ANNOTATION = "org.junit.jupiter.api.BeforeEach";
    private static final String JUNIT_DISPLAY_NAME_ANNOTATION = "org.junit.jupiter.api.DisplayName";

    public String getJunitDisplayNameAnnotation() {
        return JUNIT_DISPLAY_NAME_ANNOTATION;
    }

    public String getAllure2TmsLinkAnnotation() {
        return ALLURE2_TMS_LINK_ANNOTATION;
    }

    public String getJunitTestAnnotation() {
        return JUNIT_TEST_ANNOTATION;
    }

    public String getJunitParameterizedTestAnnotation() {
        return JUNIT_PARAMETERIZED_TEST_ANNOTATION;
    }

    public String getAllure2StepAnnotation() {
        return ALLURE2_STEP_ANNOTATION;
    }

    public String getAllure2FeatureAnnotation() {
        return ALLURE2_FEATURE_ANNOTATION;
    }

    public String getOwnerKeyAnnotation() {
        return OWNER_KEY_ANNOTATION;
    }

    public String getJunitBeforeEachAnnotation() {
        return JUNIT_BEFORE_EACH_ANNOTATION;
    }

    public String getJunitBeforeAllAnnotation() {
        return JUNIT_BEFORE_ALL_ANNOTATION;
    }

    public String getAllure2LinkAnnotation() {
        return ALLURE2_LINK_ANNOTATION;
    }

    public String getAllure2IssueAnnotation() {
        return ALLURE2_ISSUE_ANNOTATION;
    }
}
