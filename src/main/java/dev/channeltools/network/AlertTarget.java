package dev.channeltools.network;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.util.DimensionalCoord;
public final class AlertTarget {
    public final DimensionalCoord location;
    public final ForgeDirection side;
    public final ItemStack icon;
    public AlertTarget(DimensionalCoord location, ForgeDirection side, ItemStack icon) {
        this.location = location; this.side = side == null ? ForgeDirection.UNKNOWN : side; this.icon = icon;
    }
    public boolean equals(Object obj) {
        if (!(obj instanceof AlertTarget)) return false;
        AlertTarget other = (AlertTarget) obj;
        return location.equals(other.location) && side == other.side;
    }
    public int hashCode() { return 31 * location.hashCode() + side.ordinal(); }
}
