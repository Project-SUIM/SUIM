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

    // Module classes
    public static final String CLASS_HUD = "myau.mr";
    public static final String CLASS_AIM_ASSIST = "myau.mT";
    public static final String CLASS_BED_ESP = "myau.mb";
    public static final String CLASS_BED_TRACKER = "myau.mt";
    public static final String CLASS_KILL_AURA = "myau.Kf";

    // Manager classes
    public static final String CLASS_MODULE_MANAGER = "myau.mJ";
    public static final String CLASS_COMMAND_MANAGER = "myau.m7";
    public static final String CLASS_VALUE_MANAGER = "myau.mP";
    public static final String CLASS_ROTATION_MANAGER = "myau.mO";

    // RotationManager (myau.mO)
    public static final String FIELD_ROTATION_MANAGER = "N";
    public static final String FIELD_ROT_MGR_YAW_DELTA = "b";
    public static final String FIELD_ROT_MGR_PITCH_DELTA = "v";
    public static final String FIELD_ROT_MGR_ROTATED = "G";
    public static final String FIELD_ROT_MGR_PRIORITY = "B";
    public static final String METHOD_ROT_MGR_SET_ROTATION = "w";
    public static final String SIG_ROT_MGR_SET_ROTATION = "(FFJIZ)V";
    public static final String METHOD_ROT_MGR_IS_ROTATED = "u";
    public static final String METHOD_ROT_MGR_ON_TICK = "B";
    public static final String METHOD_ROT_MGR_RESET = "t";

    // Property / Value classes
    public static final String CLASS_VALUE_BASE = "myau.V";
    public static final String CLASS_BOOLEAN_PROPERTY = "myau.l";
    public static final String CLASS_INTEGER_PROPERTY = "myau.c";
    public static final String CLASS_FLOAT_PROPERTY = "myau.P";
    public static final String CLASS_STRING_PROPERTY = "myau.d";
    public static final String CLASS_ENUM_PROPERTY = "myau.n";
    public static final String CLASS_COLOR_PROPERTY = "myau.e";

    // Event base classes
    public static final String CLASS_EVENT_TYPED = "myau.U";
    public static final String CLASS_EVENT_CANCELLABLE = "myau.S";

    // Event classes
    public static final String CLASS_TICK_EVENT = "myau.KP";
    public static final String CLASS_UPDATE_EVENT = "myau.m6";
    public static final String CLASS_PACKET_EVENT = "myau.R";
    public static final String CLASS_WINDOW_CLICK_EVENT = "myau.q";
    public static final String CLASS_MOVE_INPUT_EVENT = "myau.b";
    public static final String CLASS_SWAP_ITEM_EVENT = "myau.s";

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

    // HUD (myau.mr)
    public static final String FIELD_HUD_COLOR_MODE = "k";
    public static final String FIELD_HUD_CUSTOM1 = "L";
    public static final String FIELD_HUD_CUSTOM2 = "j";
    public static final String FIELD_HUD_CUSTOM3 = "E";
    public static final String FIELD_HUD_COLOR_SPEED = "i";
    public static final String FIELD_HUD_COLOR_SATURATION = "v";
    public static final String FIELD_HUD_COLOR_BRIGHTNESS = "a";
    public static final String FIELD_HUD_SCALE = "Z";

    // BedESP (myau.mb)
    public static final String FIELD_BED_ESP_BEDS = "c";
    public static final String FIELD_BED_ESP_COLOR = "Q";
    public static final String FIELD_BED_ESP_MODE = "l";

    // RotationUtil (myau.B)
    public static final String CLASS_ROTATION_UTIL = "myau.B";
    public static final String METHOD_GET_ROTATIONS_TO_BOX = "Q";
    public static final String METHOD_GET_ROTATIONS = "G";

    // RotationState (myau.KL)
    public static final String CLASS_ROTATION_STATE = "myau.KL";
    public static final String FIELD_ROTATION_STATE_PITCH = "A";
    public static final String FIELD_ROTATION_STATE_YAW_HEAD = "T";
    public static final String FIELD_ROTATION_STATE_SMOOTH_YAW = "V";
    public static final String FIELD_ROTATION_STATE_PRIORITY = "B";
    public static final String METHOD_ROTATION_STATE_APPLY = "r";
    public static final String METHOD_ROTATION_STATE_IS_ACTIVE = "g";
    public static final String METHOD_ROTATION_STATE_GET_SMOOTHED_YAW = "j";

    // MoveUtil (myau.g)
    public static final String CLASS_MOVE_UTIL = "myau.g";
    public static final String METHOD_MOVE_UTIL_IS_FORWARD_PRESSED = "G";
    public static final String METHOD_MOVE_UTIL_FIX_STRAFE = "h";

    // SwapItemEvent (myau.s)
    public static final String METHOD_SWAP_ITEM_SET_SLOT = "x";

    // AimAssist (myau.mT)
    public static final String FIELD_AIM_ASSIST_WEAPON_ONLY = "g";
    public static final String FIELD_AIM_ASSIST_ALLOW_TOOLS = "t";
    public static final String FIELD_AIM_ASSIST_RANGE = "s";
    public static final String FIELD_AIM_ASSIST_HSPEED = "J";
    public static final String FIELD_AIM_ASSIST_VSPEED = "c";
    public static final String FIELD_AIM_ASSIST_SMOOTHING = "x";
    public static final String FIELD_AIM_ASSIST_FOV = "S";
    public static final String FIELD_AIM_ASSIST_TEAM = "K";
    public static final String FIELD_AIM_ASSIST_BOT_CHECKS = "M";
    public static final String FIELD_AIM_ASSIST_MC = "n";
    public static final String METHOD_AIM_ASSIST_ON_TICK = "d";
    public static final String METHOD_AIM_ASSIST_IS_VALID_TARGET = "e";
    public static final String METHOD_AIM_ASSIST_IS_IN_REACH = "X";
    public static final String METHOD_AIM_ASSIST_IS_LOOKING_AT_BLOCK = "F";

    // KillAura (myau.Kf)
    public static final String FIELD_KILL_AURA_TARGET = "O";
    public static final String FIELD_KILL_AURA_ATTACK_RANGE = "V";
    public static final String FIELD_KILL_AURA_SWING_RANGE = "K";
    public static final String FIELD_KILL_AURA_FOV = "X";
    public static final String FIELD_KILL_AURA_SMOOTHING = "Y";
    public static final String FIELD_KILL_AURA_DEBUG_LOG = "T";
    public static final String FIELD_KILL_AURA_SORT = "w";
    public static final String FIELD_KILL_AURA_ROTATIONS = "t";
    public static final String FIELD_KILL_AURA_MC = "v";
    public static final String FIELD_KILL_AURA_MODE = "f";
    public static final String FIELD_KILL_AURA_PLAYERS = "H";
    public static final String FIELD_KILL_AURA_MOBS = "W";
    public static final String FIELD_KILL_AURA_ANIMALS = "M";
    public static final String FIELD_KILL_AURA_TEAMS = "n";
    public static final String FIELD_KILL_AURA_BOT_CHECK = "C";
    public static final String FIELD_KILL_AURA_WEAPONS_ONLY = "E";
    public static final String FIELD_KILL_AURA_ALLOW_TOOLS = "U";
    public static final String METHOD_KILL_AURA_ON_UPDATE = "U";
    public static final String METHOD_KILL_AURA_ON_TICK = "K";
    public static final String METHOD_KILL_AURA_PERFORM_ATTACK = "B";
    public static final String METHOD_KILL_AURA_INTERACT_ATTACK = "U";
    public static final String METHOD_KILL_AURA_GET_TARGET = "l";
    public static final String METHOD_KILL_AURA_IS_VALID_TARGET = "v";
    public static final String METHOD_KILL_AURA_IS_PLAYER_TARGET = "g";

    // Value base (myau.V)
    public static final String FIELD_VALUE_CURRENT = "J";
    public static final String FIELD_VALUE_NAME = "m";
    public static final String FIELD_VALUE_OWNER = "v";

    // EventTyped (myau.U)
    public static final String FIELD_EVENT_TYPED_PRE = "PRE";
    public static final String FIELD_EVENT_TYPED_POST = "POST";
    public static final String FIELD_EVENT_TYPED_SEND = "SEND";

    // EventCancellable (myau.S)
    public static final String FIELD_EVENT_CANCELLED = "H";

    // TickEvent (myau.KP)
    public static final String FIELD_TICK_EVENT_TYPE = "p";

    // UpdateEvent (myau.m6)
    public static final String FIELD_UPDATE_EVENT_TYPE = "C";

    // PacketEvent (myau.R)
    public static final String FIELD_PACKET_EVENT_PACKET = "z";
    public static final String FIELD_PACKET_EVENT_TYPE = "M";

    // WindowClickEvent (myau.q) - unmapped fields
    public static final String FIELD_WINDOW_CLICK_MODE = "E";
    public static final String FIELD_WINDOW_CLICK_BUTTON = "z";
    public static final String FIELD_WINDOW_CLICK_SLOT = "n";

    // ModeValue (myau.n) - unmapped fields
    public static final String FIELD_ENUM_VALUES_ARRAY = "u";

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

    // RotationState method signatures
    public static final String SIG_ROTATION_STATE_IS_ACTIVE = "(SIS)Z";
    public static final String SIG_MOVE_UTIL_IS_FORWARD_PRESSED = "(CII)Z";
    public static final String SIG_MOVE_UTIL_FIX_STRAFE = "(JF)V";

    // RotationUtil method signatures
    public static final String SIG_GET_ROTATIONS_TO_BOX = "(Lnet/minecraft/util/AxisAlignedBB;JFFFF)[F";
    public static final String TARGET_GET_ROTATIONS_TO_BOX = "Lmyau/B;Q(Lnet/minecraft/util/AxisAlignedBB;JFFFF)[F";

    // UpdateEvent (myau.m6) - rotation methods
    public static final String METHOD_UPDATE_EVENT_SET_ROTATION = "H";
    public static final String SIG_UPDATE_EVENT_SET_ROTATION = "(FFI)V";
    public static final String TARGET_UPDATE_EVENT_SET_ROTATION = "Lmyau/m6;H(FFI)V";
    public static final String METHOD_UPDATE_EVENT_GET_YAW = "J";
    public static final String METHOD_UPDATE_EVENT_GET_PITCH = "T";

    // AttackData (myau.T)
    public static final String CLASS_ATTACK_DATA = "myau.T";
    public static final String FIELD_ATTACK_DATA_BOX = "m";
    public static final String METHOD_ATTACK_DATA_GET_BOX = "t";

    public static final String GENERATED_PACKAGE = "coffee.axle.suim.generated.";
    public static final String GENERATED_PACKAGE_INTERNAL = "coffee/axle/suim/generated/";
}
