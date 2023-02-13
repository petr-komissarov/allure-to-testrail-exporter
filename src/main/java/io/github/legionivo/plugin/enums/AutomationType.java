package io.github.legionivo.plugin.enums;

public enum AutomationType {
    UI_SELENIUM(1),
    BACKEND(2),
    NONE(3);

    private final int value;

    AutomationType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
