package com.sulaksono.fileingestorservice.model;

import java.util.Locale;

/**
 * Supported file extensions and helper methods.
 */
public enum FileType {

    JAVA(".java"),
    HTML(".html", ".htm"),
    JAVASCRIPT(".js"),
    CSS(".css"),
    POM(".pom"),
    MERMAID(".mmd", ".mermaid"),
    SQL(".sql"),
    PLANT_UML(".puml", ".plantuml"),
    DRAWIO(".drawio", ".dio"),
    MARKDOWN(".md", ".markdown", ".txt"),
    XML(".xml"),
    JSON(".json"),
    PROPERTIES(".properties"),
    DOCKERFILE(".dockerfile","dockerfile"),
    UNKNOWN("");

    private final String[] extensions;

    FileType(String... extensions) {
        this.extensions = extensions;
    }

    public static FileType fromFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (FileType t : values()) {
            for (String ext : t.extensions) {
                if (lower.endsWith(ext)) {
                    return t;
                }
            }
        }
        return UNKNOWN;
    }

    public boolean isTextual() {
        return this != UNKNOWN && this != DRAWIO;
    }
}