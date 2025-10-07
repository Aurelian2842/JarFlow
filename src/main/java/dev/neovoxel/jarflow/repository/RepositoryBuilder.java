package dev.neovoxel.jarflow.repository;

public class RepositoryBuilder {
    private String url;
    private String name;

    protected RepositoryBuilder() {

    }

    public RepositoryBuilder url(String url) {
        this.url = url;
        return this;
    }

    public RepositoryBuilder name(String name) {
        this.name = name;
        return this;
    }

    public Repository build() {
        return new Repository(url, name);
    }
}
