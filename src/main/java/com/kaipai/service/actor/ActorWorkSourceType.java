package com.kaipai.service.actor;

public enum ActorWorkSourceType {
    MANUAL("manual"),
    IMPORT("import"),
    MIGRATION("migration");

    private final String value;

    ActorWorkSourceType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
