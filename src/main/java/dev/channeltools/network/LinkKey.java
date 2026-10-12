package dev.channeltools.network;
/** Undirected world connection identity; dimension and bound network are held by LinksMessage. */
public final class LinkKey {
    public final int ax, ay, az, bx, by, bz;
    public LinkKey(int ax, int ay, int az, int bx, int by, int bz) {
        boolean forward = ax < bx || (ax == bx && (ay < by || (ay == by && az <= bz)));
        this.ax = forward ? ax : bx; this.ay = forward ? ay : by; this.az = forward ? az : bz;
        this.bx = forward ? bx : ax; this.by = forward ? by : ay; this.bz = forward ? bz : az;
    }
    public boolean equals(Object value) {
        if (!(value instanceof LinkKey)) return false;
        LinkKey other = (LinkKey) value;
        return ax == other.ax && ay == other.ay && az == other.az && bx == other.bx && by == other.by && bz == other.bz;
    }
    public int hashCode() {
        int h = ax; h = 31*h+ay; h = 31*h+az; h = 31*h+bx; h = 31*h+by; return 31*h+bz;
    }
}
