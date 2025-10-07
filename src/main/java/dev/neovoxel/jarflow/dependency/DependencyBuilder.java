package dev.neovoxel.jarflow.dependency;

import me.lucko.jarrelocator.Relocation;

import java.util.ArrayList;
import java.util.List;

public class DependencyBuilder {
    private String groupId;
    private String artifactId;
    private String version;
    private List<Relocation> relocations = new ArrayList<>();

    protected DependencyBuilder() {

    }

    public DependencyBuilder groupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public DependencyBuilder artifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public DependencyBuilder version(String version) {
        this.version = version;
        return this;
    }

    public DependencyBuilder relocate(String from, String to) {
        this.relocations.add(new Relocation(from, to));
        return this;
    }

    public Dependency build() {
        return new Dependency(groupId, artifactId, version, relocations);
    }
}
