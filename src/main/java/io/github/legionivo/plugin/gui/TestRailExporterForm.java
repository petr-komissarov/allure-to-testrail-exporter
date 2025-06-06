package io.github.legionivo.plugin.gui;

import com.intellij.openapi.project.Project;
import io.github.legionivo.plugin.Settings;
import io.github.legionivo.plugin.api.TestRailClient;
import io.github.legionivo.plugin.api.TestRailClientBuilder;

import javax.swing.*;
import java.util.Optional;

import static io.github.legionivo.plugin.Settings.getInstance;
import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class TestRailExporterForm {
    private JPanel rootPanel;
    private JTextField usernameTextField;
    private JTextField passwordPasswordField;
    private JTextField url;
    private JTextField projectIdTextFiled;
    private JTextField suiteIdTextField;
    private JTextField statusField;
    private JButton testButton;
    private Settings settings;
    private JCheckBox exportOnlyTestNameCheckBox;

    public TestRailExporterForm() {
        testButton.addActionListener(e -> handleTestButton());
        statusField.setText("press 'Test' button");
        exportOnlyTestNameCheckBox.setToolTipText("Create empty test case without steps");
        exportOnlyTestNameCheckBox.addActionListener(actionEvent ->
                settings.setExportOnlyTestNamesCheckBoxEnabled(exportOnlyTestNameCheckBox.isSelected()));
    }

    public void createUI(Project project) {
        settings = getInstance(project);
        usernameTextField.setText(requireNonNull(settings).getUserName());
        passwordPasswordField.setText(settings.getPassword());
        url.setText(settings.getApiUrl());
        exportOnlyTestNameCheckBox.setSelected(settings.isExportOnlyTestNamesCheckBoxEnabled());
    }

    private void handleTestButton() {
        Optional<String> username = getTestRailUser();
        if (username.isPresent()) {
            statusField.setText(String.format("authorized as '%s'", username.get()));
        } else {
            statusField.setText("Bad credentials");
        }
    }

    private boolean isEmpty(final JTextField field) {
        return isBlank(field.getText());
    }

    private Optional<String> getTestRailUser() {
        if (isEmpty(url) && isEmpty(usernameTextField) && isEmpty(passwordPasswordField)) {
            return empty();
        }
        final TestRailClient client = getTestRailClient();
        try {
            return of(client.getUserByEmail(settings.getUserName()).getName());
        } catch (Exception e) {
            return empty();
        }
    }

    private TestRailClient getTestRailClient() {
        return new TestRailClientBuilder(settings.getApiUrl(), settings.getUserName(), settings.getPassword()).build();
    }


    public JPanel getRootPanel() {
        return rootPanel;
    }

    public void apply() {
        settings.setUserName(usernameTextField.getText());
        settings.setPassword(passwordPasswordField.getText());
        settings.setApiUrl(url.getText());
        settings.setProjectId(parseInt(projectIdTextFiled.getText()));
        settings.setSuiteId(parseInt(suiteIdTextField.getText()));
        settings.setExportOnlyTestNamesCheckBoxEnabled(exportOnlyTestNameCheckBox.isSelected());
    }

    public void reset() {
        usernameTextField.setText(settings.getUserName());
        passwordPasswordField.setText(settings.getPassword());
        url.setText(settings.getApiUrl());
        projectIdTextFiled.setText(String.valueOf(settings.getProjectId()));
        suiteIdTextField.setText(String.valueOf(settings.getSuiteId()));
    }

    public boolean isModified() {
        boolean modified;
        Integer projectId = parseInt(projectIdTextFiled.getText());
        Integer suiteId = parseInt(suiteIdTextField.getText());
        modified = !usernameTextField.getText().equals(settings.getUserName());
        modified |= !passwordPasswordField.getText().equals(settings.getPassword());
        modified |= !url.getText().equals(settings.getApiUrl());
        modified |= !projectId.equals(settings.getProjectId());
        modified |= !suiteId.equals(settings.getSuiteId());
        return modified;
    }
}