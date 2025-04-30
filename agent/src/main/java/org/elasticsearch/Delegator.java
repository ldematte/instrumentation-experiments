package org.elasticsearch;

import java.util.function.Supplier;

public interface Delegator {
    <T> T delegate(Supplier<T> supplier);
}
