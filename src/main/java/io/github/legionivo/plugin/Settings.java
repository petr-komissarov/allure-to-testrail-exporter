package io.github.legionivo.plugin;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.components.ServiceManager.getService;
import static com.intellij.util.xmlb.XmlSerializerUtil.copyBean;

@State(
        name = "Settings",
        storages = {@Storage("settings.xml")}
)
public class Settings implements PersistentStateComponent<Settings> {

    private String userName;
    private String password;
    private String apiUrl;
    private int projectId;
    private int suiteId;
    private boolean isExportOnlyTestNamesCheckBoxEnabled;

    @Nullable
    public static Settings getInstance(Project project) {
        return getService(project, Settings.class);
    }

    public boolean isExportOnlyTestNamesCheckBoxEnabled() {
        return isExportOnlyTestNamesCheckBoxEnabled;
    }

    public void setExportOnlyTestNamesCheckBoxEnabled(boolean exportOnlyTestNamesCheckBoxEnabled) {
        isExportOnlyTestNamesCheckBoxEnabled = exportOnlyTestNamesCheckBoxEnabled;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(int suiteId) {
        this.suiteId = suiteId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    @Nullable
    @Override
    public Settings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull Settings settings) {
        copyBean(settings, this);
    }
}
