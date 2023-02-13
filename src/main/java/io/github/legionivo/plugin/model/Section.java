package io.github.legionivo.plugin.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Section {
    @SerializedName("id")
    @Expose
    private int id;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("description")
    @Expose
    private String description;
    @SerializedName("suite_id")
    @Expose
    private Integer suiteId;
    @SerializedName("parent_id")
    @Expose
    private Integer parentId;
    @SerializedName("depth")
    @Expose
    private int depth;
    @SerializedName("display_oder")
    @Expose
    private int displayOrder;

    public int getId() {
        return id;
    }

    public void setSuiteId(Integer suiteId) {
        this.suiteId = suiteId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
}
