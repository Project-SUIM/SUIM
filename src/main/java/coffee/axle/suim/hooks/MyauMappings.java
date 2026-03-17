package coffee.axle.suim.hooks;

/**
 * Central registry of obfuscated class, field, and method mappings for the Myau
 * client.
 *
 * <p>
 * <strong>Notice:</strong> Decompiling Myau without express permission
 * violates their Terms of Service. Using this class as reference for public
 * deobfuscation is strictly prohibited and may result in license revocation.
 * </p>
 *
 * <p>
 * This class maintains all obfuscated identifiers for Myau classes, fields, and
 * methods.
 * Update this class when Myau releases new versions, as obfuscated names change
 * with each release.
 * </p>
 *
 * <p>
 * <strong>Credits:</strong><br>
 * Myau @ksyz and the Myau development team<br>
 * SUIM developed by @axlecoffee and contributors
 * </p>
 *
 * <p>
 * <strong>Migration notes (260313 → 260317):</strong><br>
 * - ALL 208 class names re-obfuscated including single-letter classes (total name collision)<br>
 * - Key hierarchy shifts: J→n (EventBase), I→S (EventCancellable), HT→gj (ModuleBase), Q→gc (CommandBase)<br>
 * - KillAura native PERFORM_ATTACK sig changed from (FFJ)Z to (FJF)Z (param reorder)<br>
 * - KillAura native INTERACT_ATTACK sig changed from (JFF)V to (IFIFBF)V — part of "fixed not attacking enemies" fix<br>
 * - PacketEvent receive now myau.O (was myau.R); packet field M→e<br>
 * - PacketEvent send now myau.b (was myau.t); packet field s→S<br>
 * - MODULE_BASE isEnabled/setEnabled no longer share method name: isEnabled=e(), setEnabled=G(Z)V<br>
 * - 31 anti-leak protected classes (up from 28 in 260313)<br>
 * - No structural changes to property constructors or event system layout<br>
 * </p>
 *
 * @author axlecoffee
 * @author ksyz (Myau Client)
 * @author maybesomeday and others (Mappings)
 * @version 260317 (2025-03-17)
 */
public class MyauMappings {
    public static final String CLASS_MAIN = "myau.gJ";
    public static final String CLASS_MODULE_BASE = "myau.gj";
    public static final String CLASS_EVENT_BUS = "myau.h";
    public static final String CLASS_EVENT_ANNOTATION = "myau.g";
    public static final String CLASS_MODULE_COMMAND = "myau.g0";

    public static final String CLASS_EVIL_AUTUMN = "myau.a"; // please dm foxgirlfrot for information regarding this
                                                              // class — UNVERIFIED for 260317, may have changed

    // Module classes
    public static final String CLASS_HUD = "myau.vY";
    public static final String CLASS_AIM_ASSIST = "myau.g3";
    public static final String CLASS_BED_ESP = "myau.go";
    public static final String CLASS_BED_TRACKER = "myau.gP";
    public static final String CLASS_KILL_AURA = "myau.vc";
    public static final String CLASS_NO_SLOW = "myau.vB";
    public static final String CLASS_FAST_PLACE = "myau.vx";

    // Command classes
    public static final String CLASS_TARGET_COMMAND = "myau.gn";
    public static final String CLASS_FRIEND_COMMAND = "myau.ga";

    // Utility classes
    public static final String CLASS_CHAT_UTIL = "myau.g6";
    public static final String CLASS_PACKET_UTIL = "myau.J";

    // Manager classes
    public static final String CLASS_MODULE_MANAGER = "myau.k";
    public static final String CLASS_COMMAND_MANAGER = "myau.v";
    public static final String CLASS_VALUE_MANAGER = "myau.g8";
    public static final String CLASS_ROTATION_MANAGER = "myau.v4";

    // LagManager (myau.gy)
    public static final String CLASS_LAG_MANAGER = "myau.gy";
    public static final String FIELD_LAG_MANAGER = "i";
    public static final String FIELD_LAG_MGR_DELAY_TICKS = "m";
    public static final String FIELD_LAG_MGR_IS_LAGGING = "k";
    public static final String METHOD_LAG_MGR_IS_LAGGING = "L";

    // LagRange module (myau.v_)
    public static final String CLASS_LAG_RANGE = "myau.v_";
    public static final String FIELD_LAG_RANGE_TICK_COUNT = "N";
    public static final String FIELD_LAG_RANGE_ACCUMULATOR = "w";
    public static final String FIELD_LAG_RANGE_ACTIVE = "e";
    public static final String FIELD_LAG_RANGE_DELAY_VALUE = "n";

    // DelayManager (myau.gd)
    public static final String CLASS_DELAY_MANAGER = "myau.gd";

    // RotationManager (myau.v4)
    public static final String FIELD_ROTATION_MANAGER = "A";
    public static final String FIELD_ROT_MGR_YAW_DELTA = "m";
    public static final String FIELD_ROT_MGR_PITCH_DELTA = "i";
    public static final String FIELD_ROT_MGR_ROTATED = "V";
    public static final String FIELD_ROT_MGR_LAST_UPDATE = "r";
    public static final String FIELD_ROT_MGR_PRIORITY = "W";
    public static final String METHOD_ROT_MGR_SET_ROTATION = "K";
    public static final String SIG_ROT_MGR_SET_ROTATION = "(FFIZ)V"; // unchanged
    public static final String METHOD_ROT_MGR_IS_ROTATED = "W";
    public static final String METHOD_ROT_MGR_ON_TICK = "E";
    public static final String METHOD_ROT_MGR_RESET = "m"; // overloaded with pitch_delta field name — sig: (J)V

    // Property / Value classes — constructor sigs unchanged from 260313
    public static final String CLASS_VALUE_BASE = "myau.gx";
    public static final String CLASS_BOOLEAN_PROPERTY = "myau.gq";
    public static final String CLASS_INTEGER_PROPERTY = "myau.gF";
    public static final String CLASS_INT_VALUE = "myau.gb";
    public static final String CLASS_FLOAT_PROPERTY = "myau.gY";
    public static final String CLASS_STRING_PROPERTY = "myau.gA";
    public static final String CLASS_ENUM_PROPERTY = "myau.gv";
    public static final String CLASS_COLOR_PROPERTY = "myau.gM";

    // Property constructor signatures — unchanged from 260313
    // BooleanProperty: (String, Boolean)
    public static final String SIG_BOOLEAN_PROPERTY = "(Ljava/lang/String;Ljava/lang/Boolean;)V";
    public static final String SIG_BOOLEAN_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Boolean;Ljava/util/function/BooleanSupplier;)V";
    // FloatProperty: (String, Float, Float, Float)
    public static final String SIG_FLOAT_PROPERTY = "(Ljava/lang/String;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/Float;)V";
    public static final String SIG_FLOAT_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/Float;Ljava/util/function/BooleanSupplier;)V";
    // EnumProperty: (String, Integer, String[])
    public static final String SIG_ENUM_PROPERTY = "(Ljava/lang/String;Ljava/lang/Integer;[Ljava/lang/String;)V";
    public static final String SIG_ENUM_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Integer;[Ljava/lang/String;Ljava/util/function/BooleanSupplier;)V";
    // RangedValue(gF): (String, Integer, Integer, Integer) — unchanged
    public static final String SIG_RANGED_VALUE = "(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;)V";
    // ColorProperty(gM): (String, Integer) — unchanged
    public static final String SIG_COLOR_PROPERTY = "(Ljava/lang/String;Ljava/lang/Integer;)V";

    // Event base classes
    public static final String CLASS_EVENT_CANCELLABLE = "myau.S";

    // Event classes
    // TickEvent: myau.N (pre) + myau.U (post)
    public static final String CLASS_TICK_EVENT = "myau.N";           // PRE only
    public static final String CLASS_TICK_EVENT_POST = "myau.U";      // POST variant
    // UpdateEvent: myau.e (pre) + myau.j (post)
    public static final String CLASS_UPDATE_EVENT = "myau.e";         // PRE only
    public static final String CLASS_UPDATE_EVENT_POST = "myau.j";    // POST variant
    // PacketEvent: myau.O (receive) + myau.b (send) — CORRECTED: receive is O not L
    public static final String CLASS_PACKET_EVENT = "myau.O";         // RECEIVE only
    public static final String CLASS_PACKET_EVENT_SEND = "myau.b";    // SEND variant
    public static final String CLASS_RENDER_2D_EVENT = "myau.B";
    public static final String CLASS_RENDER_3D_EVENT = "myau.I";
    public static final String CLASS_WINDOW_CLICK_EVENT = "myau.s";
    public static final String CLASS_MOVE_INPUT_EVENT = "myau.Q";
    public static final String CLASS_SWAP_ITEM_EVENT = "myau.A";

    // myau.gJ (Client)
    public static final String FIELD_MODULE_MANAGER = "z";
    public static final String FIELD_COMMAND_MANAGER = "q";
    public static final String FIELD_PROPERTY_MANAGER = "w";
    public static final String FIELD_CLIENT_NAME = "M";
    public static final String FIELD_FRIEND_MANAGER = "o";
    public static final String FIELD_TARGET_MANAGER = "C";
    public static final String FIELD_EVENT_BUS = "g"; // EventBus instance field in Client

    // ChatUtil (myau.g6) — all methods are static
    public static final String METHOD_CHAT_SEND_FORMATTED = "Z";
    public static final String SIG_CHAT_SEND_FORMATTED = "(Ljava/lang/String;)V";
    public static final String METHOD_CHAT_SEND_RAW = "C";
    public static final String SIG_CHAT_SEND_RAW = "(Ljava/lang/String;)V";
    public static final String METHOD_CHAT_SEND = "z";
    public static final String SIG_CHAT_SEND = "(Lnet/minecraft/util/IChatComponent;)V";

    // PlayerListManager superclass fields (base class: myau.C)
    public static final String FIELD_PLAYER_LIST = "m";
    public static final String FIELD_PLAYER_FILE = "c";
    public static final String METHOD_PLAYER_LIST_CONTAINS = "W";

    // PacketUtil (myau.J)
    public static final String METHOD_SEND_PACKET = "N";

    // Module methods
    public static final String METHOD_GET_MODULE = "W";
    public static final String METHOD_IS_ENABLED = "e";

    // KillAura (myau.vc) — mixin-specific fields
    public static final String FIELD_KILL_AURA_AUTOBLOCK = "b";
    public static final String FIELD_KILL_AURA_IS_BLOCKING = "P";
    public static final String FIELD_KILL_AURA_TICK_STATE = "l";
    public static final String METHOD_KILL_AURA_IS_PLAYER_BLOCKING = "m";
    public static final String SIG_KILL_AURA_IS_PLAYER_BLOCKING = "()Z"; // unchanged
    public static final String METHOD_KILL_AURA_UNBLOCK = "k";           // native ()V
    public static final String SIG_KILL_AURA_UNBLOCK = "()V";            // unchanged

    // ModuleManager
    public static final String FIELD_MODULES_MAP = "U";

    // CommandManager
    public static final String FIELD_COMMANDS_LIST = "E";

    // ValueManager
    public static final String FIELD_PROPERTY_MAP = "j";

    // Module (myau.gj)
    public static final String FIELD_MODULE_NAME = "v";
    public static final String FIELD_MODULE_ENABLED = "F";
    public static final String FIELD_MODULE_KEYBIND = "B";
    public static final String METHOD_MODULE_GET_SUFFIX = "N";

    // Command (myau.gc)
    public static final String FIELD_COMMAND_NAMES = "q";

    // HUD (myau.vY)
    public static final String FIELD_HUD_COLOR_MODE = "e";
    public static final String FIELD_HUD_CUSTOM1 = "O";
    public static final String FIELD_HUD_CUSTOM2 = "S";
    public static final String FIELD_HUD_CUSTOM3 = "k";
    public static final String FIELD_HUD_COLOR_SPEED = "s";
    public static final String FIELD_HUD_COLOR_SATURATION = "b";
    public static final String FIELD_HUD_COLOR_BRIGHTNESS = "c";
    public static final String FIELD_HUD_SCALE = "q";

    // BedESP (myau.go)
    public static final String FIELD_BED_ESP_BEDS = "n";
    public static final String FIELD_BED_ESP_COLOR = "M";
    public static final String FIELD_BED_ESP_MODE = "f";
    public static final String METHOD_BED_ESP_ON_RENDER_3D = "s"; // sig: (Lmyau/I;)V — RENDER_3D is myau.I

    // RotationUtil (myau.vI)
    public static final String CLASS_ROTATION_UTIL = "myau.vI";
    public static final String METHOD_GET_ROTATIONS_TO_BOX = "a";
    public static final String METHOD_GET_ROTATIONS = "F";

    // RotationState (myau.vz)
    public static final String CLASS_ROTATION_STATE = "myau.vz";
    public static final String FIELD_ROTATION_STATE_PITCH = "c";     // UNCHANGED
    public static final String FIELD_ROTATION_STATE_YAW_HEAD = "r";
    public static final String FIELD_ROTATION_STATE_SMOOTH_YAW = "S";
    public static final String FIELD_ROTATION_STATE_PRIORITY = "o";
    public static final String METHOD_ROTATION_STATE_APPLY = "f";
    public static final String METHOD_ROTATION_STATE_IS_ACTIVE = "r";
    public static final String METHOD_ROTATION_STATE_GET_SMOOTHED_YAW = "f"; // same name as APPLY (field vs method overload)

    // MoveUtil (myau.M)
    public static final String CLASS_MOVE_UTIL = "myau.M";
    public static final String METHOD_MOVE_UTIL_IS_FORWARD_PRESSED = "r";
    public static final String METHOD_MOVE_UTIL_FIX_STRAFE = "I";

    // FastPlace (myau.vx)
    public static final String METHOD_FAST_PLACE_CAN_PLACE = "R";      // native — sig: (J)Z
    public static final String SIG_FAST_PLACE_CAN_PLACE = "(J)Z";      // unchanged
    public static final String METHOD_FAST_PLACE_ON_TICK = "j";         // sig: (Lmyau/N;)V — TickEvent is now myau.N
    public static final String SIG_FAST_PLACE_ON_TICK = "(Lmyau/N;)V";

    // NoSlow (myau.vB)
    public static final String METHOD_NO_SLOW_CHECK = "m";
    public static final String SIG_NO_SLOW_CHECK = "()Z";              // unchanged

    // SwapItemEvent (myau.A)
    public static final String METHOD_SWAP_ITEM_SET_SLOT = "o";

    // AimAssist (myau.g3)
    public static final String FIELD_AIM_ASSIST_WEAPON_ONLY = "x";
    public static final String FIELD_AIM_ASSIST_ALLOW_TOOLS = "N";
    public static final String FIELD_AIM_ASSIST_RANGE = "X";
    public static final String FIELD_AIM_ASSIST_HSPEED = "O";
    public static final String FIELD_AIM_ASSIST_VSPEED = "l";
    public static final String FIELD_AIM_ASSIST_SMOOTHING = "L";       // UNCHANGED
    public static final String FIELD_AIM_ASSIST_FOV = "U";
    public static final String FIELD_AIM_ASSIST_TEAM = "w";
    public static final String FIELD_AIM_ASSIST_BOT_CHECKS = "T";
    public static final String FIELD_AIM_ASSIST_MC = "Z";
    public static final String METHOD_AIM_ASSIST_ON_TICK = "D";           // takes TickEvent POST (myau.U) — sig: (Lmyau/U;)V
    public static final String SIG_AIM_ASSIST_ON_TICK = "(Lmyau/U;)V";    // NOTE: TickEvent POST, not myau.N (PRE)
    public static final String METHOD_AIM_ASSIST_IS_VALID_TARGET = "n";
    public static final String METHOD_AIM_ASSIST_IS_IN_REACH = "r";
    public static final String METHOD_AIM_ASSIST_IS_LOOKING_AT_BLOCK = "k";

    // KillAura (myau.vc)
    public static final String FIELD_KILL_AURA_TARGET = "iV";
    public static final String FIELD_KILL_AURA_ATTACK_RANGE = "iy";
    public static final String FIELD_KILL_AURA_SWING_RANGE = "Z";     // UNCHANGED
    public static final String FIELD_KILL_AURA_FOV = "r";
    public static final String FIELD_KILL_AURA_SMOOTHING = "p";
    public static final String FIELD_KILL_AURA_DEBUG_LOG = "D";
    public static final String FIELD_KILL_AURA_SORT = "iq";
    public static final String FIELD_KILL_AURA_ROTATIONS = "G";       // UNCHANGED
    public static final String FIELD_KILL_AURA_MC = "Q";
    public static final String FIELD_KILL_AURA_MODE = "z";
    public static final String FIELD_KILL_AURA_PLAYERS = "e";
    public static final String FIELD_KILL_AURA_MOBS = "j";
    public static final String FIELD_KILL_AURA_ANIMALS = "U";
    public static final String FIELD_KILL_AURA_TEAMS = "T";
    public static final String FIELD_KILL_AURA_BOT_CHECK = "q";
    public static final String FIELD_KILL_AURA_WEAPONS_ONLY = "g";
    public static final String FIELD_KILL_AURA_ALLOW_TOOLS = "h";
    public static final String METHOD_KILL_AURA_ON_UPDATE = "n";      // sig: (Lmyau/e;)V — UpdateEvent is now myau.e
    public static final String METHOD_KILL_AURA_ON_TICK = "G";         // sig: (Lmyau/N;)V — TickEvent is now myau.N
    public static final String METHOD_KILL_AURA_PERFORM_ATTACK = "Y";  // native — sig changed: (FJF)Z (was (FFJ)Z — param reorder)
    public static final String METHOD_KILL_AURA_INTERACT_ATTACK = "j"; // native — sig changed: (IFIFBF)V — part of "fixed not attacking enemies" fix
    public static final String METHOD_KILL_AURA_GET_TARGET = "I";
    public static final String METHOD_KILL_AURA_IS_VALID_TARGET = "S";
    public static final String SIG_KILL_AURA_IS_VALID_TARGET = "(Lnet/minecraft/entity/EntityLivingBase;J)Z"; // long param still present
    public static final String METHOD_KILL_AURA_IS_PLAYER_TARGET = "b";

    // Value base (myau.gx)
    public static final String FIELD_VALUE_CURRENT = "J";
    public static final String FIELD_VALUE_NAME = "Y";
    public static final String FIELD_VALUE_OWNER = "B";
    public static final String FIELD_VALUE_DEFAULT = "T";
    public static final String FIELD_VALUE_VISIBILITY = "U";

    // RangedValue (myau.gF) — extends gx<Integer>
    public static final String FIELD_RANGED_MIN = "X";
    public static final String FIELD_RANGED_MAX = "V";

    // FloatValue (myau.gY) — extends gx<Float>
    public static final String FIELD_FLOAT_MIN = "G";
    public static final String FIELD_FLOAT_MAX = "M";

    // IntValue (myau.gb) — extends gx<Integer>
    public static final String FIELD_INT_MIN = "P";
    public static final String FIELD_INT_MAX = "L";

    // EventCancellable (myau.S)
    public static final String FIELD_EVENT_CANCELLED = "B";

    // PacketEvent receive (myau.O) — CORRECTED: was myau.R→myau.L, actually myau.R→myau.O
    public static final String FIELD_PACKET_EVENT_PACKET = "e";
    // PacketEvent send (myau.b) — packet field is "S"
    public static final String FIELD_PACKET_EVENT_SEND_PACKET = "S";

    // WindowClickEvent (myau.s)
    public static final String FIELD_WINDOW_CLICK_MODE = "J";
    public static final String FIELD_WINDOW_CLICK_BUTTON = "u";
    public static final String FIELD_WINDOW_CLICK_SLOT = "m";

    // ModeValue (myau.gv)
    public static final String FIELD_ENUM_VALUES_ARRAY = "l";

    // Module methods (myau.gj) — NOTE: isEnabled/setEnabled no longer share name
    public static final String METHOD_GET_NAME = "s";
    public static final String METHOD_ON_ENABLE = "U";
    public static final String METHOD_ON_DISABLE = "G";
    public static final String METHOD_SET_ENABLED = "G"; // overloaded: e() = isEnabled, G(boolean) = setEnabled — G is now shared with onDisable name

    // Value methods (myau.gx)
    public static final String METHOD_PROPERTY_GET_NAME = "I";
    public static final String METHOD_PROPERTY_GET_VALUE = "g";
    public static final String METHOD_PROPERTY_SET_OWNER = "l";

    // Command (myau.gc)
    public static final String METHOD_COMMAND_RUN = "Z";

    // Event bus (myau.h)
    public static final String METHOD_EVENT_REGISTER = "J";
    public static final String FIELD_EVENT_BUS_HANDLER_MAP = "K"; // Map<Class<? extends n>, List<i>>

    // Handler wrapper (myau.i)
    public static final String CLASS_HANDLER_WRAPPER = "myau.i";
    public static final String METHOD_HANDLER_GET_HANDLER = "r"; // public Object r() { return this.j; }

    // ALL METHOD SIGS — unchanged from 260313 unless noted
    public static final String SIG_MODULE_CONSTRUCTOR = "(Ljava/lang/String;ZI)V"; // unchanged
    public static final String SIG_ON_ENABLE = "()V";                              // unchanged
    public static final String SIG_ON_DISABLE = "()V";                             // unchanged
    public static final String SIG_SET_ENABLED = "(Z)V";                           // unchanged
    public static final String SIG_COMMAND_RUN = "(Ljava/util/ArrayList;)V";       // unchanged
    public static final String SIG_COMMAND_CONSTRUCTOR = "(Ljava/util/ArrayList;)V"; // unchanged

    // RotationState method signatures
    public static final String SIG_ROTATION_STATE_IS_ACTIVE = "()Z";               // unchanged
    public static final String SIG_MOVE_UTIL_IS_FORWARD_PRESSED = "()Z";           // unchanged
    public static final String SIG_MOVE_UTIL_FIX_STRAFE = "(F)V";                  // unchanged

    // RotationUtil method signatures
    public static final String SIG_GET_ROTATIONS_TO_BOX = "(Lnet/minecraft/util/AxisAlignedBB;)[F"; // unchanged
    public static final String TARGET_GET_ROTATIONS_TO_BOX = "Lmyau/vI;a(Lnet/minecraft/util/AxisAlignedBB;)[F";

    // UpdateEvent (myau.e) - rotation methods
    public static final String METHOD_UPDATE_EVENT_SET_ROTATION = "X";
    public static final String SIG_UPDATE_EVENT_SET_ROTATION = "(FFI)V"; // unchanged
    public static final String TARGET_UPDATE_EVENT_SET_ROTATION = "Lmyau/e;X(FFI)V";
    public static final String METHOD_UPDATE_EVENT_GET_YAW = "j";
    public static final String METHOD_UPDATE_EVENT_GET_PITCH = "U";

    // AttackData (myau.r)
    public static final String CLASS_ATTACK_DATA = "myau.r";
    public static final String FIELD_ATTACK_DATA_BOX = "P"; // was "d" — public final field
    public static final String METHOD_ATTACK_DATA_GET_BOX = "P"; // field is public; direct access via .P

    public static final String GENERATED_PACKAGE = "coffee.axle.suim.generated.";
    public static final String GENERATED_PACKAGE_INTERNAL = "coffee/axle/suim/generated/";
}
