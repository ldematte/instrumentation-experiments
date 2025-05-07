package org.elasticsearch;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.elasticsearch.Util.delegate;
import static org.elasticsearch.Util.propagateDelegation;

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

    public void anotherSensitiveMethod() {
        delegate(c -> {
            if (Files.exists(pathToWatch)) {
                System.out.println("Watching " + pathToWatch);
            }
        });
    }

    public void someSensitiveMethodOnDifferentThread() {
        delegate(c -> {
            new Thread(() -> {
                propagateDelegation(c, () -> {
                    if (Files.exists(pathToWatch)) {
                        System.out.println("Watching " + pathToWatch);
                    }
                });
            }).start();
        });
    }
}

