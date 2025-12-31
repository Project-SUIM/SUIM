package coffee.axle.suim.features;

public interface Feature {
    boolean initialize() throws Exception;

    String getName();

    default void disable() throws Exception {
    }
}
