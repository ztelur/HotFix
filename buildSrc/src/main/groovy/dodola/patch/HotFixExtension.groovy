package dodola.patch

import org.gradle.api.Project

public class HotFixExtension{
    HashSet<String> includePackage = []
    HashSet<String> excludeClass = []
    boolean debugOn = true

    HotFixExtension(Project project) {
    }
}