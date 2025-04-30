package org.elasticsearch;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Supplier;

public class Main {

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

        EntitlementChecker.allowed = true;

        var fileWatcher = new AbstractFileWatchingService(new Delegator() {
            @Override
            public <T> T delegate(Supplier<T> supplier) {
                return supplier.get();
            }
        });

//        System.out.println("Calling Files.newOutputStream a 2nd time");
//        try (var x = Files.newOutputStream(Files.createTempFile("test", "test"))) {
//            x.write(10);
//        }
        System.out.println("Calling System.exit a 2nd time");
        System.exit(0);
        throw new RuntimeException("Should not reach this point");
    }
}
