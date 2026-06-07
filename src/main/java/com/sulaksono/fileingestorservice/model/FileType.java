package com.sulaksono.fileingestorservice.model;

import java.util.Locale;

/**
 * Supported file extensions and helper methods.
 */
public enum FileType {

    // ---- Build / project manifests ----
    POM("pom.xml", ".pom"),
    MAVEN_WRAPPER("mvnw", "mvnw.cmd"),

    GRADLE("build.gradle", "settings.gradle", ".gradle",
            "build.gradle.kts", "settings.gradle.kts", ".gradle.kts"),
    GRADLE_WRAPPER("gradlew", "gradlew.bat"),

    COMPOSER("composer.json", "composer.lock"),

    CARGO("cargo.toml", "cargo.lock"),                 // Cargo.toml/Cargo.lock
    PYPROJECT("pyproject.toml"),
    PYTHON_DEPS("requirements.txt", "pipfile", "pipfile.lock", "setup.py", "setup.cfg"),

    CMAKE("cmakelists.txt", ".cmake"),                 // CMakeLists.txt
    MAKEFILE("makefile", "gnumakefile"),

    DOTNET_SOLUTION(".sln"),
    DOTNET_PROJECT(".csproj", ".fsproj", ".vbproj"),
    NUGET("packages.config", ".nuspec"),

    // ---- Languages ----
    JAVA(".java"),
    PHP(".php", ".phtml", ".php3", ".php4", ".php5", ".php7", ".php8", ".phpt", ".phps"),
    PYTHON(".py", ".pyi"),
    C(".c", ".h"),
    CPP(".cpp", ".cxx", ".cc", ".hpp", ".hxx", ".hh"),
    CSHARP(".cs", ".csx"),
    RUST(".rs"),

    HTML(".html", ".htm"),
    JAVASCRIPT(".js", ".mjs", ".cjs"),
    CSS(".css"),

    // ---- Docs / diagrams / data / config ----
    MERMAID(".mmd", ".mermaid"),
    SQL(".sql"),
    PLANT_UML(".puml", ".plantuml"),
    DRAWIO(".drawio", ".dio"),
    MARKDOWN(".md", ".markdown"),
    YAML(".yml", ".yaml"),
    TOML(".toml"),
    XML(".xml"),
    JSON(".json"),
    PROPERTIES(".properties"),
    DOCKERFILE("dockerfile", "docker-compose.yml", "docker-compose.yaml", ".dockerfile"),
    CSV(".csv"),
    TXT(".txt"),

    UNKNOWN("");

    private final String[] patterns;

    FileType(String... patterns) {
        this.patterns = patterns;
    }

    public static FileType fromFileName(String name) {
        if (name == null || name.isBlank()) return UNKNOWN;

        String lower = name.toLowerCase(Locale.ROOT);
        String normalized = lower.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);

        for (FileType t : values()) {
            for (String p : t.patterns) {
                if (p == null || p.isEmpty()) continue;

                String pl = p.toLowerCase(Locale.ROOT);

                if (pl.startsWith(".")) {
                    if (baseName.endsWith(pl)) return t;
                } else {
                    if (baseName.equals(pl)) return t;
                }
            }
        }
        return UNKNOWN;
    }

    public boolean isTextual() {
        return this != UNKNOWN && this != DRAWIO;
    }
}