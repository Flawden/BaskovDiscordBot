package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenRepositoryRoutingContractTest {

    @Test
    void customRepositoriesAreRestrictedToTheirOwnedGroupIds() throws Exception {
        Path filterDir = Path.of(".mvn/rrf");
        Path releases = filterDir.resolve("groupId-lavalink-releases.txt");
        Path snapshots = filterDir.resolve("groupId-lavalink-libdave-snapshots.txt");

        assertEquals(List.of("dev.lavalink.youtube"), nonBlankLines(releases));
        assertEquals(List.of("moe.kyokobot.libdave"), nonBlankLines(snapshots));
        assertFalse(Files.exists(filterDir.resolve("groupId-central.txt")),
                "Maven Central must remain unrestricted");
    }

    @Test
    void mavenHelperEnablesGroupIdFilteringForVerifyAndVersionResolution() throws Exception {
        String helper = Files.readString(Path.of(".github/scripts/maven-ci.sh"));

        assertTrue(helper.contains("-Daether.remoteRepositoryFilter.groupId=true"));
        assertTrue(helper.contains("-Daether.remoteRepositoryFilter.groupId.basedir=${MAVEN_RRF_DIR}"));
        assertTrue(helper.contains("readonly MAVEN_RRF_DIR=\"${REPO_ROOT}/.mvn/rrf\""));
        assertTrue(count(helper, "${MAVEN_REPOSITORY_FILTER_ARGS[@]}") >= 2,
                "Repository routing must apply to verify and project-version resolution");
        assertTrue(helper.contains("maven_remote_repository_filter=groupId"));
        assertTrue(helper.contains("printf '[maven-ci] %s\\n' \"$*\" >&2"),
                "Helper diagnostics must stay on stderr so version output is machine-readable");
    }

    @Test
    void filterFileNamesMatchPomRepositoryIdsAndOwnedDependencies() throws Exception {
        Document pom = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
        Element project = pom.getDocumentElement();
        Element repositories = directChild(project, "repositories");

        assertTrue(repositoryIds(repositories).contains("lavalink-releases"));
        assertTrue(repositoryIds(repositories).contains("lavalink-libdave-snapshots"));
        assertTrue(hasDependency(project, "dev.lavalink.youtube", "v2"));
        assertTrue(hasDependency(project, "moe.kyokobot.libdave", "adapter-jda"));
        assertTrue(hasDependency(project, "moe.kyokobot.libdave", "impl-jni"));
    }

    private static List<String> nonBlankLines(Path path) throws Exception {
        return Files.readAllLines(path).stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static long count(String source, String needle) {
        long count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static List<String> repositoryIds(Element repositories) {
        return directChildren(repositories, "repository").stream()
                .map(repository -> directChildText(repository, "id"))
                .toList();
    }

    private static boolean hasDependency(Element project, String groupId, String artifactId) {
        Element dependencies = directChild(project, "dependencies");
        return directChildren(dependencies, "dependency").stream().anyMatch(dependency ->
                groupId.equals(directChildText(dependency, "groupId"))
                        && artifactId.equals(directChildText(dependency, "artifactId")));
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

    private static List<Element> directChildren(Element parent, String name) {
        java.util.ArrayList<Element> result = new java.util.ArrayList<>();
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
