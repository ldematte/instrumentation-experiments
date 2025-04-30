package org.elasticsearch;

import java.nio.file.Files;
import java.nio.file.Path;

public class AbstractFileWatchingService {

    private final Delegator delegator;
    public Path pathToWatch = Path.of("./");

    public AbstractFileWatchingService(Delegator delegator) {
        this.delegator = delegator;
    }

    public void someSensitiveMethod() {
        if (delegator.delegate(() -> Files.exists(pathToWatch))) {
            System.out.println("Watching " + pathToWatch);
        }
    }
}

