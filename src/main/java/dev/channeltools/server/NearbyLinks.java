package dev.channeltools.server;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import dev.channeltools.network.LinkKey;

/** Keeps the nearest valid rendered lines without retaining an entire large ME network. */
final class NearbyLinks {

    private final int limit;
    private final Map<LinkKey, Entry> entries = new HashMap<>();
    private final PriorityQueue<Entry> farthestFirst = new PriorityQueue<>((a, b) -> compare(b, a));

    NearbyLinks(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("Link limit must be positive");
        this.limit = limit;
    }

    void offer(LinkKey key, int free, double distanceSquared) {
        if (free < 0) return;
        Entry previous = entries.get(key);
        if (previous != null) {
            // Several multipart connections may share the same rendered line.
            previous.free = Math.min(previous.free, free);
            return;
        }

        Entry candidate = new Entry(key, free, distanceSquared);
        if (entries.size() == limit) {
            if (compare(candidate, farthestFirst.peek()) >= 0) return;
            entries.remove(farthestFirst.remove().key);
        }
        entries.put(key, candidate);
        farthestFirst.add(candidate);
    }

    Map<LinkKey, Integer> toMap() {
        Map<LinkKey, Integer> result = new HashMap<>();
        for (Entry entry : entries.values()) result.put(entry.key, entry.free);
        return result;
    }

    private static int compare(Entry a, Entry b) {
        int result = Double.compare(a.distanceSquared, b.distanceSquared);
        if (result != 0) return result;
        // Stable tie-breaking makes the selection independent of AE2's set iteration order.
        result = Integer.compare(a.key.ax, b.key.ax);
        if (result == 0) result = Integer.compare(a.key.ay, b.key.ay);
        if (result == 0) result = Integer.compare(a.key.az, b.key.az);
        if (result == 0) result = Integer.compare(a.key.bx, b.key.bx);
        if (result == 0) result = Integer.compare(a.key.by, b.key.by);
        if (result == 0) result = Integer.compare(a.key.bz, b.key.bz);
        return result;
    }

    private static final class Entry {
        final LinkKey key;
        final double distanceSquared;
        int free;

        Entry(LinkKey key, int free, double distanceSquared) {
            this.key = key;
            this.free = free;
            this.distanceSquared = distanceSquared;
        }
    }
}
