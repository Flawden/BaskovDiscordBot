package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyCompatibilityContractTest {

    @Test
    void productionFrameworkLineRemainsOnKnownGreenBaseline() throws Exception {
        Document pom = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());

        Element project = pom.getDocumentElement();
        Element parent = directChild(project, "parent");

        assertEquals("3.4.3", directChildText(parent, "version"),
                "Spring Boot 4 requires an explicit migration release");
        assertEquals("6.5.0", dependencyVersion(project, "net.dv8tion", "JDA"),
                "JDA 6.5.0 is the isolated DAVE voice migration line");
        assertEquals("2.2.3", dependencyVersion(project, "dev.arbjerg", "lavaplayer"));
        assertEquals("1.18.36", dependencyVersion(project, "org.projectlombok", "lombok"));
        assertEquals("3.13.0", pluginVersion(project,
                "org.apache.maven.plugins", "maven-compiler-plugin"));
    }

    @Test
    void workflowsRemainOnLastGreenActionMajors() throws Exception {
        String ci = Files.readString(Path.of(".github", "workflows", "ci.yml"));
        String delivery = Files.readString(Path.of(".github", "workflows", "delivery.yml"));
        String workflows = ci + "\n" + delivery;

        assertTrue(workflows.contains("actions/checkout@v4"));
        assertTrue(workflows.contains("actions/setup-java@v4"));
        assertTrue(workflows.contains("actions/upload-artifact@v4"));
        assertTrue(workflows.contains("docker/setup-buildx-action@v3"));
        assertTrue(workflows.contains("docker/login-action@v3"));
        assertTrue(workflows.contains("docker/build-push-action@v6"));

        assertFalse(workflows.contains("actions/checkout@v7"));
        assertFalse(workflows.contains("docker/build-push-action@v7"));
    }

    @Test
    void dependabotDoesNotOfferMajorUpdatesAsRoutineMaintenance() throws Exception {
        String dependabot = Files.readString(Path.of(".github", "dependabot.yml"));

        assertEquals(2, occurrences(dependabot, "version-update:semver-major"),
                "Both Maven and GitHub Actions ecosystems must ignore routine major updates");
        assertEquals(2, occurrences(dependabot, "dependency-name: \"*\""));
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

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }
}
