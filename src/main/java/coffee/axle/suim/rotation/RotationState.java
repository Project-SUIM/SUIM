package coffee.axle.suim.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraftforge.common.MinecraftForge;

/**
 * Singleton server-side rotation tracker.
 * <p>
 * Observes dispatched C03/C05/C06 packets to maintain an accurate
 * picture of what yaw/pitch the server believes the player has.
 * Also snapshots client-side angles before any feature spoofs them.
 * <p>
 * Register once via {@link #register()} during feature init; shared
 * by all rotation consumers (AimAssist, AutoBlockIn, etc.).
 *
 * @author axle.coffee
 */
public final class RotationState {

    private static final RotationState INSTANCE = new RotationState();
    private static final Minecraft mc = Minecraft.getMinecraft();

    private float serverYaw;
    private float serverPitch;

    /** Client-side angles captured before any feature modifies them. */
    private float clientYaw;
    private float clientPitch;

    private boolean initialized;

    private RotationState() {
    }

    public static RotationState getInstance() {
        return INSTANCE;
    }

    /**
     * Register on the Forge event bus. Safe to call multiple times -
     * only registers once.
     */
    public void register() {
        if (!initialized) {
            MinecraftForge.EVENT_BUS.register(this);
            initialized = true;
        }
    }

    /**
     * Snapshot the player's current (visual) rotation before any feature
     * spoofs it. Call once per tick at the start of rotation processing.
     */
    public void captureClientAngles() {
        if (mc.thePlayer != null) {
            clientYaw = mc.thePlayer.rotationYaw;
            clientPitch = mc.thePlayer.rotationPitch;
        }
    }

    /**
     * Force-set the server angles (e.g. on module enable when no packet
     * has been sent yet).
     */
    public void setServerAngles(float yaw, float pitch) {
        this.serverYaw = yaw;
        this.serverPitch = pitch;
    }

    public float getServerYaw() {
        return serverYaw;
    }

    public float getServerPitch() {
        return serverPitch;
    }

    public float getClientYaw() {
        return clientYaw;
    }

    public float getClientPitch() {
        return clientPitch;
    }

    private float estimatedPingTicks;

    public void updatePing() {
        if (mc.thePlayer == null || mc.getNetHandler() == null)
            return;
        try {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null) {
                estimatedPingTicks = info.getResponseTime() / 50.0f;
            }
        } catch (Exception ignored) {
        }
    }

    public float getEstimatedPingTicks() {
        return estimatedPingTicks;
    }
}
