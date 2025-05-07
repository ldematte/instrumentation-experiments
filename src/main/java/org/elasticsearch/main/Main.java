package org.elasticsearch.main;

import org.elasticsearch.AbstractFileWatchingService;
import org.elasticsearch.DelegateChecks;

import java.io.IOException;

public class Main {

    static class FileWatchingServiceFactory {
        @DelegateChecks
        static AbstractFileWatchingService create() {
            return new org.elasticsearch.generated.DelegatingAbstractFileWatchingService();
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Calling System.exit");
        try {
            System.exit(1);
        } catch (SecurityException ex) {
            System.out.println("No exit!");
        }
        System.out.println("Calling Files.newOutputStream");
//        try (var x = Files.newOutputStream(Files.createTempFile("test", "test"))) {
//            x.write(10);
//            throw new RuntimeException("Should not reach this point");
//        } catch (UnsupportedOperationException ex) {
//            System.out.println("No open!");
//        }


        // var fileWatcher = FileWatchingServiceFactory.create();
        var fileWatcher = new AbstractFileWatchingService(null);
        try {
            fileWatcher.anotherSensitiveMethod();
        } catch (SecurityException ex) {
            assert ex.getMessage().equals("class org.elasticsearch.main.Main not allowed");
        }

//        System.out.println("Calling Files.newOutputStream a 2nd time");
//        try (var x = Files.newOutputStream(Files.createTempFile("test", "test"))) {
//            x.write(10);
//        }
    }
}
