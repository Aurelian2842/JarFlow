package dev.neovoxel.jarflow.dependency;

import lombok.Getter;
import lombok.ToString;
import me.lucko.jarrelocator.Relocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
@ToString
public class Dependency {
    @NotNull
    private final String groupId;

    @NotNull
    private final String artifactId;

    @NotNull
    private final String version;

    @NotNull
    private final List<Relocation> relocations = new ArrayList<>();

    protected Dependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version, @NotNull List<Relocation> relocations) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.relocations.addAll(relocations);
    }

    public static DependencyBuilder builder() {
        return new DependencyBuilder();
    }
}
