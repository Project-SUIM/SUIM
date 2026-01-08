package coffee.axle.suim.features;

public interface Feature {
    /**
     * Called when the feature should initialize
     * 
     * @return true if successful, false otherwise
     */
    boolean initialize() throws Exception;

    /**
     * Get the feature name
     */
    String getName();

    /**
     * Called when the feature should be disabled
     */
    default void disable() throws Exception {
    }
}
