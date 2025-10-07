package dev.neovoxel.jarflow;

import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.jarflow.repository.Repository;
import dev.neovoxel.jarflow.util.DependencyDownloader;
import dev.neovoxel.jarflow.util.ExternalLoader;
import dev.neovoxel.jarflow.util.MetadataParser;
import lombok.Setter;
import me.lucko.jarrelocator.JarRelocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class JarFlow {

    private static final List<Repository> repositories = new ArrayList<>();

    private static Map<Dependency, Repository> dependencies = new HashMap<>();

    private static final List<Dependency> loaded = new ArrayList<>();

    @Setter
    private static int threadCount = 4;

    @Setter
    private static File libDir = new File("libs");

    private static Logger logger = LoggerFactory.getLogger(JarFlow.class);

    static {
        ExternalLoader.init();
    }


    public static void addRepository(Repository repository) {
        repositories.add(repository);
    }

    public static void addRepositories(Collection<Repository> repositories) {
        JarFlow.repositories.addAll(repositories);
    }

    public static void addRepositories(Repository... repositories) {
        for (Repository repository : repositories) {
            addRepository(repository);
        }
    }

    public static void loadDependency(Dependency dependency) throws Throwable {
        logger.info("Loading dependency: {}", dependency.toString());
        dependencies = MetadataParser.resolve(Collections.singletonList(dependency), dependencies, repositories);
        for (Dependency dependency2 : dependencies.keySet()) {
            if (loaded.contains(dependency2)) {
                continue;
            }
            String fileName = dependency2.getArtifactId() + "-" + dependency2.getVersion();
            Path path = libDir.toPath()
                    .resolve(dependency2.getGroupId())
                    .resolve(dependency2.getArtifactId())
                    .resolve(dependency2.getVersion())
                    .resolve(fileName + ".jar");
            if (!hasDownloaded(dependency2)) DependencyDownloader.download(JarFlow.dependencies.get(dependency2), dependency2, libDir, threadCount);
            if (dependency.getRelocations().isEmpty()) {
                ExternalLoader.load(path.toFile());
            } else {
                fileName += "-relocated.jar";
                JarRelocator jarRelocator = new JarRelocator(path.toFile(), path.resolve("../" + fileName).toFile(), dependency.getRelocations());
                jarRelocator.run();
                ExternalLoader.load(path.resolve("../" + fileName).toFile());
            }
            loaded.add(dependency2);
        }
    }

    public static void loadDependencies(Collection<Dependency> dependencies) throws Throwable {
        logger.info("Loading dependencies: {}", Arrays.toString(dependencies.toArray()));
        JarFlow.dependencies = MetadataParser.resolve(dependencies, JarFlow.dependencies, repositories);
        for (Dependency dependency : JarFlow.dependencies.keySet()) {
            if (loaded.contains(dependency)) {
                continue;
            }
            String fileName = dependency.getArtifactId() + "-" + dependency.getVersion();
            Path path = libDir.toPath()
                    .resolve(dependency.getGroupId())
                    .resolve(dependency.getArtifactId())
                    .resolve(dependency.getVersion())
                    .resolve(fileName + ".jar");
            if (!hasDownloaded(dependency)) DependencyDownloader.download(JarFlow.dependencies.get(dependency), dependency, libDir, threadCount);
            if (dependency.getRelocations().isEmpty()) {
                ExternalLoader.load(path.toFile());
            } else {
                fileName += "-relocated.jar";
                JarRelocator jarRelocator = new JarRelocator(path.toFile(), path.resolve("../" + fileName).toFile(), dependency.getRelocations());
                jarRelocator.run();
                ExternalLoader.load(path.resolve("../" + fileName).toFile());
            }
            loaded.add(dependency);
        }
    }

    private static boolean hasDownloaded(Dependency dependency) {
        Path path = libDir.toPath()
                .resolve(dependency.getGroupId())
                .resolve(dependency.getArtifactId())
                .resolve(dependency.getVersion())
                .resolve(dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar");
        if (path.toFile().exists()) {
            return true;
        }
        return false;
    }
}
