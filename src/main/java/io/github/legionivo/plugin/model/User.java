package io.github.legionivo.plugin.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("email")
    @Expose
    private String email;
    @SerializedName("id")
    @Expose
    private Integer id;
    @SerializedName("is_active")
    @Expose
    private Boolean isActive;
    @SerializedName("name")
    @Expose
    private String name;

    public String getName() {
        return name;
    }
}
