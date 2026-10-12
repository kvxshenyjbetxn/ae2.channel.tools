package dev.channeltools.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import dev.channeltools.network.LinkKey;
import dev.channeltools.network.LinksMessage;

public class NearbyLinksTest {

    @Test
    public void closerLinesDisplaceTheFirstLinesFound() {
        NearbyLinks links = new NearbyLinks(2);
        links.offer(key(10), 8, 100);
        links.offer(key(20), 4, 400);
        links.offer(key(1), 0, 1);
        Map<LinkKey, Integer> selected = links.toMap();
        assertEquals(2, selected.size());
        assertTrue(selected.containsKey(key(1)));
        assertTrue(selected.containsKey(key(10)));
        assertFalse(selected.containsKey(key(20)));
    }

    @Test
    public void coincidentMultipartLinesKeepTheirTightestValidCapacity() {
        NearbyLinks links = new NearbyLinks(2);
        LinkKey forward = key(1);
        LinkKey reverse = new LinkKey(forward.bx, forward.by, forward.bz, forward.ax, forward.ay, forward.az);
        links.offer(forward, 8, 1);
        links.offer(reverse, 2, 1);
        links.offer(forward, 7, 1);
        links.offer(forward, -1, 1);
        assertEquals(1, links.toMap().size());
        assertEquals(Integer.valueOf(2), links.toMap().get(forward));
    }

    @Test
    public void unknownCapacityDoesNotConsumeThePacketLimit() {
        NearbyLinks links = new NearbyLinks(1);
        links.offer(key(1), -1, 1);
        links.offer(key(5), 32, 25);
        assertEquals(1, links.toMap().size());
        assertEquals(Integer.valueOf(32), links.toMap().get(key(5)));
    }

    @Test
    public void equalDistancesHaveDeterministicSelection() {
        NearbyLinks forward = new NearbyLinks(2);
        NearbyLinks backward = new NearbyLinks(2);
        for (int i = 0; i < 5; i++) forward.offer(key(i), i, 10);
        for (int i = 4; i >= 0; i--) backward.offer(key(i), i, 10);
        assertEquals(forward.toMap(), backward.toMap());
        assertTrue(forward.toMap().containsKey(key(0)));
        assertTrue(forward.toMap().containsKey(key(1)));
    }

    @Test
    public void selectionMatchesSortingForMoreThanTheProductionPacketLimit() {
        int count = LinksMessage.MAX_LINKS + 1000;
        Random random = new Random(42);
        List<Integer> order = new ArrayList<>();
        double[] distances = new double[count];
        NearbyLinks links = new NearbyLinks(LinksMessage.MAX_LINKS);
        for (int i = 0; i < count; i++) {
            distances[i] = random.nextDouble() * 1024;
            order.add(i);
            links.offer(key(i), i % 33, distances[i]);
        }
        assertEquals(LinksMessage.MAX_LINKS, links.toMap().size());
        order.sort(Comparator.comparingDouble((Integer i) -> distances[i]).thenComparingInt(i -> i));
        Map<LinkKey, Integer> expected = new HashMap<>();
        for (int i = 0; i < LinksMessage.MAX_LINKS; i++) {
            int index = order.get(i);
            expected.put(key(index), index % 33);
        }
        assertEquals(expected, links.toMap());
    }

    @Test
    public void emittedMapsAreIndependentSnapshots() {
        NearbyLinks links = new NearbyLinks(1);
        links.offer(key(1), 8, 1);
        Map<LinkKey, Integer> snapshot = links.toMap();
        links.offer(key(1), 0, 1);
        assertEquals(Integer.valueOf(8), snapshot.get(key(1)));
        snapshot.clear();
        assertEquals(Integer.valueOf(0), links.toMap().get(key(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesAnEmptySelectionLimit() {
        new NearbyLinks(0);
    }

    private static LinkKey key(int x) {
        return new LinkKey(x, 64, 0, x, 64, 1);
    }
}
