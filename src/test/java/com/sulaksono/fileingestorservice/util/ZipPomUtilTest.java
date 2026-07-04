package com.sulaksono.fileingestorservice.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipPomUtilTest {

    @Test
    void extractArtifactId_fromPomXml_shouldReturnArtifactId() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo-service</artifactId>
                    <version>1.2.3</version>
                </project>
                """;

        byte[] zip = zipWithEntry("pom.xml", pom);

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("demo-service");
    }

    @Test
    void extractVersion_fromPomXml_shouldReturnVersion() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo-service</artifactId>
                    <version>2.0.0</version>
                </project>
                """;

        byte[] zip = zipWithEntry("pom.xml", pom);

        String result = ZipPomUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("2.0.0");
    }

    @Test
    void extractArtifactId_fromNestedPomXml_shouldReturnArtifactId() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>nested-service</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        byte[] zip = zipWithEntry("project/subproject/pom.xml", pom);

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("nested-service");
    }

    @Test
    void extractArtifactId_fromDotPomFile_shouldReturnArtifactId() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>dot-pom-service</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        byte[] zip = zipWithEntry("META-INF/maven/com.example/demo-service/pom.pom", pom);

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("dot-pom-service");
    }

    @Test
    void extractArtifactId_whenChildMissing_shouldFallBackToParent() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-service</artifactId>
                        <version>9.9.9</version>
                    </parent>
                </project>
                """;

        byte[] zip = zipWithEntry("pom.xml", pom);

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("parent-service");
    }

    @Test
    void extractVersion_whenChildMissing_shouldFallBackToParent() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>child-service</artifactId>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-service</artifactId>
                        <version>9.9.9</version>
                    </parent>
                </project>
                """;

        byte[] zip = zipWithEntry("pom.xml", pom);

        String result = ZipPomUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("9.9.9");
    }

    @Test
    void extractArtifactId_shouldPreferChildOverParent() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>child-service</artifactId>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-service</artifactId>
                        <version>9.9.9</version>
                    </parent>
                    <version>1.0.0</version>
                </project>
                """;

        byte[] zip = zipWithEntry("pom.xml", pom);

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("child-service");
    }

    @Test
    void extractVersion_shouldPreferChildOverParent() throws Exception {
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>child-service</artifactId>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-service</artifactId>
                        <version>9.9.9</version>
                    </parent>
                    <version>1.0.0</version>
                </project>
                """;

        byte[] zip = zipWithEntry("pom.xml", pom);

        String result = ZipPomUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isEqualTo("1.0.0");
    }

    @Test
    void extractArtifactId_whenNoPomInZip_shouldReturnNull() throws Exception {
        byte[] zip = zipWithEntry("README.md", "# demo");

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenNoPomInZip_shouldReturnNull() throws Exception {
        byte[] zip = zipWithEntry("README.md", "# demo");

        String result = ZipPomUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractArtifactId_whenPomXmlIsMalformed_shouldReturnNull() throws Exception {
        byte[] zip = zipWithEntry("pom.xml", "<project><this-is-not-valid></project>");

        String result = ZipPomUtil.extractArtifactId(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenPomXmlIsMalformed_shouldReturnNull() throws Exception {
        byte[] zip = zipWithEntry("pom.xml", "not xml at all");

        String result = ZipPomUtil.extractVersion(
                new ByteArrayInputStream(zip)
        );

        assertThat(result).isNull();
    }

    @Test
    void extractArtifactId_whenInputIsNotZip_shouldReturnNull() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                "not a zip".getBytes(StandardCharsets.UTF_8)
        );

        String result = ZipPomUtil.extractArtifactId(input);

        assertThat(result).isNull();
    }

    @Test
    void extractVersion_whenInputIsNotZip_shouldReturnNull() {
        ByteArrayInputStream input = new ByteArrayInputStream(
                "not a zip".getBytes(StandardCharsets.UTF_8)
        );

        String result = ZipPomUtil.extractVersion(input);

        assertThat(result).isNull();
    }

    @Test
    void constructor_shouldBePrivate() throws Exception {
        Constructor<ZipPomUtil> constructor =
                ZipPomUtil.class.getDeclaredConstructor();

        assertThat(constructor.canAccess(null)).isFalse();

        constructor.setAccessible(true);
        ZipPomUtil instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }

    private static byte[] zipWithEntry(String entryName, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            ZipEntry entry = new ZipEntry(entryName);
            zip.putNextEntry(entry);
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        return bytes.toByteArray();
    }
}