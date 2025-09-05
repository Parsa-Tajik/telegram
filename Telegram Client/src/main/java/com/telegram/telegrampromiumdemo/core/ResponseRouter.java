package com.telegram.telegrampromiumdemo.core;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public final class ResponseRouter {
    private final ConcurrentMap<Long, Waiter> waiters = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    public long register(Predicate<String> matcher) {
        long id = seq.getAndIncrement();
        waiters.put(id, new Waiter(matcher));
        return id;
    }

    public CompletableFuture<String> futureOf(long id) {
        Waiter w = waiters.get(id);
        if (w == null) throw new IllegalStateException("No waiter: " + id);
        return w.future;
    }

    public void dispatch(String line) {
        for (Map.Entry<Long, Waiter> e : waiters.entrySet()) {
            Waiter w = e.getValue();
            if (!w.future.isDone() && w.matcher.test(line)) {
                w.future.complete(line);
                waiters.remove(e.getKey());
                return;
            }
        }
        System.out.println("[SERVER] " + line);
    }

    private static final class Waiter {
        final Predicate<String> matcher;
        final CompletableFuture<String> future = new CompletableFuture<>();
        Waiter(Predicate<String> matcher) { this.matcher = matcher; }
    }
}
