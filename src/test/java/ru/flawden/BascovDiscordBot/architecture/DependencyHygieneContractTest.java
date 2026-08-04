package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyHygieneContractTest {

    private static final Set<String> BOOT_MANAGED_LOGGING_DEPENDENCIES = Set.of(
            "org.slf4j:slf4j-api",
            "ch.qos.logback:logback-classic",
            "ch.qos.logback:logback-core"
    );

    @Test
    void loggingVersionsRemainOwnedBySpringBootAndAndroidJsonStaysExcluded() throws Exception {
        Document document = parsePom();

        assertEquals("0.4.4", directChildText(document.getDocumentElement(), "version"));
        assertFalse(propertyNames(document).contains("discord4j-core.version"),
                "Unused Discord4J property must not return");

        Set<String> directDependencies = directDependencyCoordinates(document);
        assertTrue(BOOT_MANAGED_LOGGING_DEPENDENCIES.stream().noneMatch(directDependencies::contains),
                "SLF4J and Logback must be supplied by Spring Boot dependency management");

        assertTrue(starterTestExclusions(document).contains("com.vaadin.external.google:android-json"),
                "android-json must stay excluded to avoid duplicate org.json.JSONObject implementations");
    }

    @Test
    void testClasspathContainsOneJsonObjectImplementation() throws Exception {
        List<URL> implementations = Collections.list(
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResources("org/json/JSONObject.class")
        );

        assertEquals(1, implementations.size(),
                () -> "Expected exactly one org.json.JSONObject implementation but found " + implementations);
    }

    @Test
    void testsUseDedicatedLoggingConfigurationWithoutFileAppender() throws Exception {
        Path testLogging = Path.of("src", "test", "resources", "logback-test.xml");
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(testLogging.toFile());

        NodeList appenders = document.getElementsByTagName("appender");
        assertEquals(1, appenders.getLength(), "Tests should use only one console appender");
        Element appender = (Element) appenders.item(0);
        assertEquals("CONSOLE", appender.getAttribute("name"));
        assertFalse(appender.getAttribute("class").contains("File"),
                "Tests must not write rolling log files into the checkout");
    }

    private static Document parsePom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
    }

    private static Set<String> propertyNames(Document document) {
        Set<String> names = new HashSet<>();
        NodeList properties = document.getElementsByTagName("properties");
        if (properties.getLength() == 0) {
            return names;
        }
        NodeList children = properties.item(0).getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                names.add(node.getNodeName());
            }
        }
        return names;
    }

    private static Set<String> directDependencyCoordinates(Document document) {
        Set<String> coordinates = new HashSet<>();
        Element project = document.getDocumentElement();
        Element dependencies = directChild(project, "dependencies");
        if (dependencies == null) {
            return coordinates;
        }
        for (Element dependency : directChildren(dependencies, "dependency")) {
            coordinates.add(coordinate(dependency));
        }
        return coordinates;
    }

    private static Set<String> starterTestExclusions(Document document) {
        Element dependencies = directChild(document.getDocumentElement(), "dependencies");
        if (dependencies == null) {
            return Set.of();
        }
        for (Element dependency : directChildren(dependencies, "dependency")) {
            if ("org.springframework.boot:spring-boot-starter-test".equals(coordinate(dependency))) {
                Element exclusions = directChild(dependency, "exclusions");
                if (exclusions == null) {
                    return Set.of();
                }
                Set<String> result = new HashSet<>();
                for (Element exclusion : directChildren(exclusions, "exclusion")) {
                    result.add(coordinate(exclusion));
                }
                return result;
            }
        }
        return Set.of();
    }

    private static String coordinate(Element element) {
        return directChildText(element, "groupId") + ":" + directChildText(element, "artifactId");
    }

    private static String directChildText(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static Element directChild(Element parent, String name) {
        for (Element child : directChildren(parent, name)) {
            return child;
        }
        return null;
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> result = new ArrayList<>();
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
