package coffee.axle.suim.property;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a field into myau.
 *
 * <p>The name is the field name in kebab-case ({@code onlyTopBeds} ->
 * {@code only-top-beds}) use {@link #name()} to override it! Types map via passed
 * field type: boolean, int, float, String, any enum, and int + {@link #color()}
 * for argb. Numeric fields need {@link #min()}/{@link #max()} when they are
 * not 0..100.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Setting {

    /** @param name is the appropriate name, must be {@code lower-case} as such (kebab-case)
     *  @expects name in lowercase kebab-case
     *  @default is the kebabby version of the field name
     */
    String name() default "";

    /** bind to a property myau already provides on the target module instead of creating one */
    boolean existing() default false;

    /** display names for a mode setting; default is the enum constant names */
    String[] names() default {};

    double min() default 0;

    double max() default 100;

    /**
     * Whether this setting represents a color value {@code ARGB} rather than a plain number
     * @return true if this setting is a colour
     */
    boolean color() default false;

    /**
     * The name of the {@code Setting} after which this setting should append itself in myau's chat commands and everything else
     */
    String after() default "";
}
