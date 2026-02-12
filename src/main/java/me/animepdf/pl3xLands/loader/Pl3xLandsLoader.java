package me.animepdf.pl3xLands.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public class Pl3xLandsLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(
                new RemoteRepository.Builder(
                        "central",
                        "default",
                        getDefaultMavenCentralMirror()
                ).build()
        );
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:2.3.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.spongepowered:configurate-hocon:4.2.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.spongepowered:configurate-extra-kotlin:4.2.0"), null));

        classpathBuilder.addLibrary(resolver);
    }

    private static String getDefaultMavenCentralMirror() {
        String central = System.getenv("PAPER_DEFAULT_CENTRAL_REPOSITORY");
        if (central == null) {
            central = System.getProperty("org.bukkit.plugin.java.LibraryLoader.centralURL");
        }
        if (central == null) {
            central = "https://maven-central.storage-download.googleapis.com/maven2";
        }
        return central;
    }
}