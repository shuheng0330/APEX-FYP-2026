package com.tbm.careerpathlearning.aspect;

/** Thread-confined logging scope; asynchronous workers must enter their own scope. */
public final class MetadataOnlyLogScope implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private final Thread owner = Thread.currentThread();
    private boolean closed;

    private MetadataOnlyLogScope() {}

    public static MetadataOnlyLogScope enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
        return new MetadataOnlyLogScope();
    }

    public static boolean active() {
        return DEPTH.get() != null;
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Logging scope must close on its owning thread");
        if (closed) return;
        int depth = DEPTH.get();
        if (depth == 1) DEPTH.remove();
        else DEPTH.set(depth - 1);
        closed = true;
    }
}
