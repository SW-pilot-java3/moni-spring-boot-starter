package com.moni.domain.metric.push;

import com.moni.domain.metric.dto.request.MetricsPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RetryQueue {

    private final Deque<MetricsPayload> queue = new ArrayDeque<>();
    private final int capacity;

    public synchronized void offer(MetricsPayload payload) {
        if (capacity <= 0) {
            return;
        }
        while (queue.size() >= capacity) {
            queue.pollFirst();
        }
        queue.addLast(payload);
    }

    public synchronized List<MetricsPayload> drainAll() {
        List<MetricsPayload> drained = new ArrayList<>(queue);
        queue.clear();
        return drained;
    }
}
