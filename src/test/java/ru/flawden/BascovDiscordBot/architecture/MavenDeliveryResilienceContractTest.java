package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenDeliveryResilienceContractTest {

    @Test
    void hostedWorkflowsExposeTransfersUseStableCacheIdentityAndBoundMavenExecution() throws Exception {
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        String delivery = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String workflows = ci + "\n" + delivery;
        String helper = Files.readString(Path.of(".github/scripts/maven-ci.sh"));

        assertTrue(workflows.contains("actions/setup-java@v5"));
        assertFalse(workflows.contains("actions/setup-java@v4"));
        assertTrue(workflows.contains("cache-dependency-path: .github/maven-cache-key.txt"));
        assertTrue(workflows.contains("show-download-progress: true"));
        assertTrue(workflows.contains("SEGMENT_DOWNLOAD_TIMEOUT_MINS: '2'"));
        assertTrue(workflows.contains("./.github/scripts/maven-ci.sh diagnose"));
        assertTrue(workflows.contains("./.github/scripts/maven-ci.sh verify"));
        assertTrue(workflows.contains("./.github/scripts/maven-ci.sh version"));
        assertFalse(workflows.contains("--no-transfer-progress clean verify"));

        assertTrue(helper.contains("https://repo.maven.apache.org/maven2/"));
        assertTrue(helper.contains("https://maven.lavalink.dev/releases/"));
        assertTrue(helper.contains("https://maven.lavalink.dev/snapshots/"));
        assertTrue(helper.contains("--connect-timeout 5 --max-time 15"));
        assertTrue(helper.contains("MAVEN_VERIFY_ATTEMPTS:-2"));
        assertTrue(helper.contains("MAVEN_VERIFY_TIMEOUT_SECONDS:-420"));
        assertTrue(helper.contains("timeout --signal=TERM --kill-after=30s"));
        assertTrue(helper.contains("*.lastUpdated"));
        assertTrue(helper.contains("Could not transfer artifact"));
        assertTrue(helper.contains("non-network build/test error"));
    }

    @Test
    void dependencyCacheIdentityTracksPinnedRuntimeAndBuildDependenciesWithoutApplicationVersion() throws Exception {
        Properties cacheKey = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".github/maven-cache-key.txt"))) {
            cacheKey.load(reader);
        }

        Document pom = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
        Element project = pom.getDocumentElement();
        Element parent = directChild(project, "parent");
        Element properties = directChild(project, "properties");

        assertEquals(directChildText(parent, "version"), cacheKey.getProperty("spring-boot"));
        assertEquals(dependencyVersion(project, "net.dv8tion", "JDA"), cacheKey.getProperty("jda"));
        assertEquals(dependencyVersion(project, "dev.arbjerg", "lavaplayer"), cacheKey.getProperty("lavaplayer"));
        assertEquals(directChildText(properties, "youtube-source.version"), cacheKey.getProperty("youtube-source"));
        assertEquals(directChildText(properties, "libdave-jvm.version"), cacheKey.getProperty("libdave"));
        assertEquals(dependencyVersion(project, "org.projectlombok", "lombok"), cacheKey.getProperty("lombok"));
        assertEquals(pluginVersion(project, "org.apache.maven.plugins", "maven-compiler-plugin"),
                cacheKey.getProperty("maven-compiler-plugin"));

        String wrapper = Files.readString(Path.of(".mvn/wrapper/maven-wrapper.properties"));
        assertTrue(wrapper.contains("wrapperVersion=" + cacheKey.getProperty("maven-wrapper-script")));
        assertTrue(wrapper.contains("apache-maven-" + cacheKey.getProperty("maven-wrapper") + "-bin.zip"));
        assertFalse(Files.readString(Path.of(".github/maven-cache-key.txt")).contains("1.6.1"),
                "Application-only release bumps must not invalidate the dependency cache");
    }

    private static String dependencyVersion(Element project, String groupId, String artifactId) {
        Element dependencies = directChild(project, "dependencies");
        for (Element dependency : directChildren(dependencies, "dependency")) {
            if (groupId.equals(directChildText(dependency, "groupId"))
                    && artifactId.equals(directChildText(dependency, "artifactId"))) {
                return directChildText(dependency, "version");
            }
        }
        return "";
    }

    private static String pluginVersion(Element project, String groupId, String artifactId) {
        Element build = directChild(project, "build");
        Element plugins = directChild(build, "plugins");
        for (Element plugin : directChildren(plugins, "plugin")) {
            if (groupId.equals(directChildText(plugin, "groupId"))
                    && artifactId.equals(directChildText(plugin, "artifactId"))) {
                return directChildText(plugin, "version");
            }
        }
        return "";
    }

    private static String directChildText(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static Element directChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Element child : directChildren(parent, name)) {
            return child;
        }
        return null;
    }

    private static java.util.List<Element> directChildren(Element parent, String name) {
        java.util.List<Element> result = new java.util.ArrayList<>();
        if (parent == null) {
            return result;
        }
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }
}
