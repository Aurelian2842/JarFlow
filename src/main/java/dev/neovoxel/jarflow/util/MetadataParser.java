package dev.neovoxel.jarflow.util;

import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.jarflow.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class MetadataParser {

    private static final Logger logger = LoggerFactory.getLogger(MetadataParser.class);

    public static Map<Dependency, Repository> resolve(Collection<Dependency> dependencies, Map<Dependency, Repository> resolved, Collection<Repository> repositories) {
        Set<String> missingDeps = new HashSet<>();
        for (Dependency dependency : dependencies) {
            Map.Entry<Map<Dependency, Repository>, List<Dependency>> entry = parse(dependency, repositories);
            for (Map.Entry<Dependency, Repository> subDependency : entry.getKey().entrySet()) {
                if (!contains(resolved.keySet(), subDependency.getKey(), true)) {
                    resolved.put(subDependency.getKey(), subDependency.getValue());
                }
            }
            for (Dependency subDependency : entry.getValue()) {
                String depKey = subDependency.getGroupId() + ":" + subDependency.getArtifactId();
                missingDeps.add(depKey);
            }
        }
        for (String missingDependency : missingDeps) {
            if (contains(resolved.keySet(), Dependency.builder().groupId(missingDependency.split(":")[0]).artifactId(missingDependency.split(":")[1]).version("null").build(), false)) {
                missingDeps.remove(missingDependency);
            }
        }
        return parseMissing(resolved, missingDeps.stream()
                .map((depKey) -> Dependency.builder().groupId(depKey.split(":")[0]).artifactId(depKey.split(":")[1]).version("null").build())
                .collect(Collectors.toList()), repositories);
    }

    public static Map<Dependency, Repository> resolve(Collection<Dependency> dependencies, Collection<Repository> repositories) {
        return resolve(dependencies, new HashMap<>(), repositories);
    }

    public static Map<Dependency, Repository> resolve(Dependency dependency, Collection<Repository> repositories) {
        Map.Entry<Map<Dependency, Repository>, List<Dependency>> entry = parse(dependency, repositories);
        return parseMissing(entry.getKey(), entry.getValue(), repositories);
    }

    private static Map<Dependency, Repository> parseMissing(Map<Dependency, Repository> dependencies,
                                                            List<Dependency> missingDependencies,
                                                            Collection<Repository> repositories) {
        Set<String> processedDeps = new HashSet<>();
        for (Dependency missingDependency : missingDependencies) {
            String depKey = missingDependency.getGroupId() + ":" + missingDependency.getArtifactId();
            if (processedDeps.contains(depKey)) {
                continue;
            }
            processedDeps.add(depKey);
            if (contains(dependencies.keySet(), missingDependency, false)) {
                continue;
            }
            String latestVersion = getLatestVersion(
                    missingDependency.getGroupId(),
                    missingDependency.getArtifactId(),
                    repositories
            );
            if (latestVersion != null) {
                Dependency versionedDep = Dependency.builder()
                        .groupId(missingDependency.getGroupId())
                        .artifactId(missingDependency.getArtifactId())
                        .version(latestVersion)
                        .build();
                Map.Entry<Map<Dependency, Repository>, List<Dependency>> entry = parse(versionedDep, repositories);
                for (Map.Entry<Dependency, Repository> subDependency : entry.getKey().entrySet()) {
                    if (!contains(dependencies.keySet(), subDependency.getKey(), true)) {
                        dependencies.put(subDependency.getKey(), subDependency.getValue());
                    }
                }
                parseMissing(dependencies, entry.getValue(), repositories);
            } else {
                logger.warn("Could not find latest version for dependency {}: {}",
                        missingDependency.getGroupId(), missingDependency.getArtifactId());
            }
        }
        return dependencies;
    }

    @NotNull
    private static Map.Entry<Map<Dependency, Repository>, List<Dependency>> parse(Dependency dependency, Collection<Repository> repositories) {
        Map<Dependency, Repository> subDependencies = new HashMap<>();
        List<Dependency> missingDependencies = new ArrayList<>();
        if ("null".equals(dependency.getVersion())) {
            return new AbstractMap.SimpleEntry<>(subDependencies, Arrays.asList(dependency));
        }
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(dependency.getGroupId().replace(".", "/"));
        urlBuilder.append("/");
        urlBuilder.append(dependency.getArtifactId());
        urlBuilder.append("/");
        urlBuilder.append(dependency.getVersion());
        urlBuilder.append("/");
        String pomFileName = dependency.getArtifactId() + "-" + dependency.getVersion() + ".pom";
        urlBuilder.append(pomFileName);
        String urlPath = urlBuilder.toString();

        for (Repository repo : repositories) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(repo.getUrl() + urlPath);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    StringBuilder content = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line);
                        }
                    }
                    String xmlContent = content.toString();
                    JSONObject json = XML.toJSONObject(replaceProperties(xmlContent));
                    subDependencies.put(dependency, repo);
                    if (json.getJSONObject("project").has("dependencies")) {
                        JSONObject dependenciesObj = json.getJSONObject("project").getJSONObject("dependencies");
                        JSONArray dependencies = new JSONArray();
                        if (dependenciesObj.optJSONArray("dependency", new JSONArray()).length() == 0) {
                            dependencies.put(dependenciesObj.getJSONObject("dependency"));
                        } else {
                            dependencies = dependenciesObj.getJSONArray("dependency");
                        }
                        for (Object obj : dependencies) {
                            JSONObject jsonObject = (JSONObject) obj;
                            String groupId = jsonObject.getString("groupId");
                            String artifactId = jsonObject.getString("artifactId");
                            if (jsonObject.has("scope")) {
                                String scope = jsonObject.getString("scope");
                                if (scope.equals("compile") || scope.equals("test")) continue;
                            }
                            boolean hasVersion = true;
                            String version;
                            if (!jsonObject.has("version")) {
                                hasVersion = false;
                                version = "null";
                            } else {
                                version = jsonObject.get("version").toString();
                            }
                            Dependency subDependency = Dependency.builder()
                                    .groupId(groupId)
                                    .artifactId(artifactId)
                                    .version(version)
                                    .build();
                            if (contains(subDependencies.keySet(), subDependency, true)) {
                                continue;
                            }
                            if (hasVersion) {
                                Map.Entry<Map<Dependency, Repository>, List<Dependency>> entry = parse(subDependency, repositories);
                                subDependencies.putAll(entry.getKey());
                                missingDependencies.addAll(entry.getValue());
                            } else {
                                missingDependencies.add(subDependency);
                            }
                        }
                    }
                    return new AbstractMap.SimpleEntry<>(subDependencies, missingDependencies);
                }
            } catch (Exception e) {
                logger.error("Failed to resolve dependency {} from {}", dependency, repo);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        logger.error("Failed to resolve dependency {}", dependency);
        return new AbstractMap.SimpleEntry<>(new HashMap<>(), new ArrayList<>());
    }

    public static String replaceProperties(String content) {
        JSONObject json = XML.toJSONObject(content);
        if (!json.getJSONObject("project").has("properties")) {
            return content;
        }
        JSONObject properties = json.getJSONObject("project").getJSONObject("properties");
        for (String key : properties.keySet()) {
            content = content.replace("${" + key + "}", properties.getString(key));
        }
        return content;
    }

    @Nullable
    public static String getLatestVersion(String groupId, String artifactId, Collection<Repository> repositories) {
        for (Repository repo : repositories) {
            String urlPath = repo.getUrl() + groupId.replace(".", "/") + "/" + artifactId + "/maven-metadata.xml";
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlPath);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    StringBuilder content = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line);
                        }
                    }
                    String xmlContent = content.toString();
                    JSONObject json = XML.toJSONObject(xmlContent);
                    JSONArray versions = json.getJSONObject("metadata")
                            .getJSONObject("versioning")
                            .getJSONObject("versions")
                            .getJSONArray("version");
                    String latestVersion = (String) versions.get(versions.length() - 1);
                    return latestVersion;
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    public static boolean contains(Collection<Dependency> dependencies, Dependency dependency, boolean matchVersion) {
        for (Dependency dep : dependencies) {
            if (dep.getGroupId().equals(dependency.getGroupId()) && dep.getArtifactId().equals(dependency.getArtifactId())) {
                if (matchVersion && dep.getVersion().equals(dependency.getVersion())) {
                    return true;
                } else if (!matchVersion) {
                    return true;
                }
            }
        }
        return false;
    }
}
