package club.guidelight.helper;

public class NukkitMaterial {
    public final String name;
    public final boolean isSolid;
    public final boolean isLiquid;
    public NukkitMaterial(String n, boolean sol, boolean liq) {
        name = n;
        isSolid = sol;
        isLiquid = liq;
    }
}
