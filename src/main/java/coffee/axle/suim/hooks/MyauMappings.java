package coffee.axle.suim.hooks;

/**
 * All Myau class/field/method mappings in one place meow
 * Realistically this should be the only thing that needs to be updated every
 * Myau update (since obf should change the package names)
 */
public class MyauMappings {
    public static final String CLASS_MAIN = "myau.X";
    public static final String CLASS_MODULE_BASE = "myau.mD";
    public static final String CLASS_EVENT_BUS = "myau.mE";
    public static final String CLASS_EVENT_ANNOTATION = "myau.mB";
    public static final String CLASS_MODULE_COMMAND = "myau.KU";

    // Property classes
    public static final String CLASS_BOOLEAN_PROPERTY = "myau.l";
    public static final String CLASS_INTEGER_PROPERTY = "myau.c";
    public static final String CLASS_FLOAT_PROPERTY = "myau.P";
    public static final String CLASS_STRING_PROPERTY = "myau.d";
    public static final String CLASS_ENUM_PROPERTY = "myau.n";

    // myau.X
    public static final String FIELD_MODULE_MANAGER = "j";
    public static final String FIELD_COMMAND_MANAGER = "i";
    public static final String FIELD_PROPERTY_MANAGER = "e";
    public static final String FIELD_CLIENT_NAME = "R";

    // Module
    public static final String FIELD_MODULES_MAP = "E";

    // Command
    public static final String FIELD_COMMANDS_LIST = "Z";

    // Property
    public static final String FIELD_PROPERTY_MAP = "o";

    // Module
    public static final String FIELD_MODULE_NAME = "o";
    public static final String FIELD_MODULE_ENABLED = "p";
    public static final String FIELD_MODULE_KEYBIND = "P";

    // Command
    public static final String FIELD_COMMAND_NAMES = "n";

    // Module
    public static final String METHOD_GET_NAME = "J";
    public static final String METHOD_ON_ENABLE = "h";
    public static final String METHOD_ON_DISABLE = "S";
    public static final String METHOD_SET_ENABLED = "f";

    // Property
    public static final String METHOD_PROPERTY_GET_NAME = "B";
    public static final String METHOD_PROPERTY_GET_VALUE = "J";
    public static final String METHOD_PROPERTY_SET_OWNER = "e";

    // Command
    public static final String METHOD_COMMAND_RUN = "J";

    // Event bus
    public static final String METHOD_EVENT_REGISTER = "c";

    // ALL METHOD SIGS
    public static final String SIG_MODULE_CONSTRUCTOR = "(Ljava/lang/String;ZIJ)V";
    public static final String SIG_ON_ENABLE = "(SIC)V";
    public static final String SIG_ON_DISABLE = "(J)V";
    public static final String SIG_SET_ENABLED = "(SBSI)V";
    public static final String SIG_COMMAND_RUN = "(Ljava/util/ArrayList;J)V";
    public static final String SIG_COMMAND_CONSTRUCTOR = "(Ljava/util/ArrayList;)V";

    public static final String GENERATED_PACKAGE = "coffee.axle.suim.generated.";
    public static final String GENERATED_PACKAGE_INTERNAL = "coffee/axle/suim/generated/";
}
