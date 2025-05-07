package org.elasticsearch;

public class EntitlementCheckerHandle {
    @SuppressWarnings("unused")
    public static EntitlementChecker instance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final EntitlementChecker instance = new EntitlementCheckerImpl();
    }

    // no construction
    private EntitlementCheckerHandle() {}
}
