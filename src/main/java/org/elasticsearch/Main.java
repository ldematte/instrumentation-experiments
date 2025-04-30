package org.elasticsearch;

import java.io.IOException;

public class Main {

    static class FileWatchingServiceFactory {
        @DelegateChecks
        static AbstractFileWatchingService create() {
            return new DelegatingAbstractFileWatchingService();
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Calling System.exit");
        try {
            System.exit(1);
        } catch (UnsupportedOperationException ex) {
            System.out.println("No exit!");
        }
        System.out.println("Calling Files.newOutputStream");
//        try (var x = Files.newOutputStream(Files.createTempFile("test", "test"))) {
//            x.write(10);
//            throw new RuntimeException("Should not reach this point");
//        } catch (UnsupportedOperationException ex) {
//            System.out.println("No open!");
//        }

        EntitlementCheckerImpl.allowed = true;

        var fileWatcher = FileWatchingServiceFactory.create();

//        System.out.println("Calling Files.newOutputStream a 2nd time");
//        try (var x = Files.newOutputStream(Files.createTempFile("test", "test"))) {
//            x.write(10);
//        }
        System.out.println("Calling System.exit a 2nd time");
        System.exit(0);
        throw new RuntimeException("Should not reach this point");
    }
}
