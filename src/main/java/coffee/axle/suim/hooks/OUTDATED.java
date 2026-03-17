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
 * <strong>Migration notes (250910 → 260313):</strong><br>
 * - ZKM trash parameters removed from most method signatures (onEnable, onDisable, setEnabled, getSuffix, commandRun, etc.)<br>
 * - Event system restructured: EventTyped removed; TickEvent/UpdateEvent/PacketEvent split into separate PRE/POST/SEND/RECEIVE classes<br>
 * - RotationManager.setRotation lost its long parameter<br>
 * - EventBus.register changed from static to instance method<br>
 * - New Timer module added (ZQ was LagRange rename, Zk is new Timer)<br>
 * - KillAura gained HYPIXEL+ autoblock mode (10th option)<br>
 * - FastPlace canPlace check method is now native<br>
 * - Several methods moved to native code for anti-leak protection<br>
 * - AttackData box field is now public (no getter needed)<br>
 * </p>
 *
 * @author axlecoffee
 * @author ksyz (Myau Client)
 * @author maybesomeday and others (Mappings)
 * @version 260313 (2025-03-13)
 */
public class OUTDATED {
    public static final String CLASS_MAIN = "myau.Zd";
    public static final String CLASS_MODULE_BASE = "myau.HT";
    public static final String CLASS_EVENT_BUS = "myau.H2";
    public static final String CLASS_EVENT_ANNOTATION = "myau.Ho";
    public static final String CLASS_MODULE_COMMAND = "myau.T";

    public static final String CLASS_EVIL_AUTUMN = "myau.a"; // please dm foxgirlfrot for information regarding this
                                                             // class — UNVERIFIED for 260313, may have changed

    // Module classes
    public static final String CLASS_HUD = "myau.H_";
    public static final String CLASS_AIM_ASSIST = "myau.HJ";
    public static final String CLASS_BED_ESP = "myau.Hd";
    public static final String CLASS_BED_TRACKER = "myau.Hv";
    public static final String CLASS_KILL_AURA = "myau.HD";
    public static final String CLASS_NO_SLOW = "myau.ZM";
    public static final String CLASS_FAST_PLACE = "myau.H7";        // old myau.m5

    // Command classes
    public static final String CLASS_TARGET_COMMAND = "myau.z";     // old myau.KA
    public static final String CLASS_FRIEND_COMMAND = "myau.y";     // old myau.KW

    // Utility classes
    public static final String CLASS_CHAT_UTIL = "myau.Z0";
    public static final String CLASS_PACKET_UTIL = "myau.D";

    // Manager classes
    public static final String CLASS_MODULE_MANAGER = "myau.HZ";
    public static final String CLASS_COMMAND_MANAGER = "myau.ZL";
    public static final String CLASS_VALUE_MANAGER = "myau.o";
    public static final String CLASS_ROTATION_MANAGER = "myau.ZU";

    // LagManager (myau.ZP)
    public static final String CLASS_LAG_MANAGER = "myau.ZP";
    public static final String FIELD_LAG_MANAGER = "T";
    public static final String FIELD_LAG_MGR_DELAY_TICKS = "d";
    public static final String FIELD_LAG_MGR_IS_LAGGING = "W";
    public static final String METHOD_LAG_MGR_IS_LAGGING = "a";

    // LagRange module (myau.ZQ)
    public static final String CLASS_LAG_RANGE = "myau.ZQ";
    public static final String FIELD_LAG_RANGE_TICK_COUNT = "p";
    public static final String FIELD_LAG_RANGE_ACCUMULATOR = "y";
    public static final String FIELD_LAG_RANGE_ACTIVE = "B";
    public static final String FIELD_LAG_RANGE_DELAY_VALUE = "C";

    // DelayManager (myau.j)
    public static final String CLASS_DELAY_MANAGER = "myau.j";

    // RotationManager (myau.ZU)
    public static final String FIELD_ROTATION_MANAGER = "K";
    public static final String FIELD_ROT_MGR_YAW_DELTA = "c";
    public static final String FIELD_ROT_MGR_PITCH_DELTA = "m";
    public static final String FIELD_ROT_MGR_ROTATED = "W";
    public static final String FIELD_ROT_MGR_LAST_UPDATE = "s";
    public static final String FIELD_ROT_MGR_PRIORITY = "q";
    public static final String METHOD_ROT_MGR_SET_ROTATION = "N";
    public static final String SIG_ROT_MGR_SET_ROTATION = "(FFIZ)V"; // was (FFJIZ)V — long param removed
    public static final String METHOD_ROT_MGR_IS_ROTATED = "r";
    public static final String METHOD_ROT_MGR_ON_TICK = "G";
    public static final String METHOD_ROT_MGR_RESET = "N"; // overloaded with setRotation — sig: (J)V

    // Property / Value classes — ALL long ZKM trash params removed from public constructors in 260313
    public static final String CLASS_VALUE_BASE = "myau.H1";
    public static final String CLASS_BOOLEAN_PROPERTY = "myau.Hm";
    public static final String CLASS_INTEGER_PROPERTY = "myau.H3";
    public static final String CLASS_INT_VALUE = "myau.HY";
    public static final String CLASS_FLOAT_PROPERTY = "myau.HA";
    public static final String CLASS_STRING_PROPERTY = "myau.Hk";
    public static final String CLASS_ENUM_PROPERTY = "myau.Hq";
    public static final String CLASS_COLOR_PROPERTY = "myau.Hy";

    // Property constructor signatures — long trash removed in 260313
    // BooleanProperty: was (String, Boolean, long) → now (String, Boolean)
    public static final String SIG_BOOLEAN_PROPERTY = "(Ljava/lang/String;Ljava/lang/Boolean;)V";
    public static final String SIG_BOOLEAN_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Boolean;Ljava/util/function/BooleanSupplier;)V";
    // FloatProperty: was (long, String, Float, Float, Float) → now (String, Float, Float, Float)
    public static final String SIG_FLOAT_PROPERTY = "(Ljava/lang/String;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/Float;)V";
    public static final String SIG_FLOAT_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/Float;Ljava/util/function/BooleanSupplier;)V";
    // EnumProperty: was (String, long, Integer, String[]) → now (String, Integer, String[])
    public static final String SIG_ENUM_PROPERTY = "(Ljava/lang/String;Ljava/lang/Integer;[Ljava/lang/String;)V";
    public static final String SIG_ENUM_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Integer;[Ljava/lang/String;Ljava/util/function/BooleanSupplier;)V";
    // RangedValue(H3): (String, Integer, Integer, Integer) — no long in old or new
    public static final String SIG_RANGED_VALUE = "(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;)V";
    // ColorProperty(Hy): (String, Integer) — no long in old or new
    public static final String SIG_COLOR_PROPERTY = "(Ljava/lang/String;Ljava/lang/Integer;)V";

    // Event base classes
    // NOTE: EventTyped (old myau.U) was REMOVED in 260313.
    // Events are now split into separate PRE/POST classes.
    // public static final String CLASS_EVENT_TYPED = "myau.U"; // REMOVED
    public static final String CLASS_EVENT_CANCELLABLE = "myau.I";

    // Event classes — RESTRUCTURED: events split from single typed class into separate classes
    // TickEvent: OLD myau.KP (single class) → NEW myau.V (pre) + myau.b (post)
    public static final String CLASS_TICK_EVENT = "myau.V";           // PRE only
    public static final String CLASS_TICK_EVENT_POST = "myau.b";      // NEW: POST variant
    // UpdateEvent: OLD myau.m6 (single class) → NEW myau.E (pre) + myau.w (post)
    public static final String CLASS_UPDATE_EVENT = "myau.E";         // PRE only
    public static final String CLASS_UPDATE_EVENT_POST = "myau.w";    // NEW: POST variant
    // PacketEvent: OLD myau.R (single class) → NEW myau.R (receive) + myau.t (send)
    public static final String CLASS_PACKET_EVENT = "myau.R";         // RECEIVE only
    public static final String CLASS_PACKET_EVENT_SEND = "myau.t";    // NEW: SEND variant
    public static final String CLASS_RENDER_3D_EVENT = "myau.n";      // old myau.mj; extends myau.J (Event)
    public static final String CLASS_WINDOW_CLICK_EVENT = "myau.l";
    public static final String CLASS_MOVE_INPUT_EVENT = "myau.HQ";
    public static final String CLASS_SWAP_ITEM_EVENT = "myau.F";

    // myau.Zd (Client)
    public static final String FIELD_MODULE_MANAGER = "L";
    public static final String FIELD_COMMAND_MANAGER = "w";
    public static final String FIELD_PROPERTY_MANAGER = "V";
    public static final String FIELD_CLIENT_NAME = "a";
    public static final String FIELD_FRIEND_MANAGER = "F";
    public static final String FIELD_TARGET_MANAGER = "J";
    public static final String FIELD_EVENT_BUS = "A"; // NEW: EventBus instance field in Client

    // ChatUtil (myau.Z0) — all methods are static; long trash params removed in 260313
    public static final String METHOD_CHAT_SEND_FORMATTED = "P"; // was d(String,long) → P(String)
    public static final String SIG_CHAT_SEND_FORMATTED = "(Ljava/lang/String;)V"; // was (Ljava/lang/String;J)V
    public static final String METHOD_CHAT_SEND_RAW = "b";       // was l(String,long) → b(String)
    public static final String SIG_CHAT_SEND_RAW = "(Ljava/lang/String;)V";       // was (Ljava/lang/String;J)V
    public static final String METHOD_CHAT_SEND = "l";           // was a(long,IChatComponent) → l(IChatComponent)
    public static final String SIG_CHAT_SEND = "(Lnet/minecraft/util/IChatComponent;)V"; // was (JLnet/minecraft/util/IChatComponent;)V

    // PlayerListManager superclass fields (base class: myau.r)
    public static final String FIELD_PLAYER_LIST = "H";
    public static final String FIELD_PLAYER_FILE = "U";
    public static final String METHOD_PLAYER_LIST_CONTAINS = "s";

    // PacketUtil (myau.D)
    public static final String METHOD_SEND_PACKET = "u";

    // Module methods
    public static final String METHOD_GET_MODULE = "x";
    public static final String METHOD_IS_ENABLED = "y";

    // KillAura (myau.HD) — mixin-specific fields
    public static final String FIELD_KILL_AURA_AUTOBLOCK = "s";
    public static final String FIELD_KILL_AURA_IS_BLOCKING = "H";
    public static final String FIELD_KILL_AURA_TICK_STATE = "u";
    public static final String METHOD_KILL_AURA_IS_PLAYER_BLOCKING = "p"; // was Q(int,int,char) — trash removed → p()
    public static final String SIG_KILL_AURA_IS_PLAYER_BLOCKING = "()Z"; // was (IIC)Z
    public static final String METHOD_KILL_AURA_UNBLOCK = "e";           // old r(J)V → native e()V — sends RELEASE_USE_ITEM
    public static final String SIG_KILL_AURA_UNBLOCK = "()V";            // was (J)V — long trash removed, now native

    // ModuleManager
    public static final String FIELD_MODULES_MAP = "T";

    // CommandManager
    public static final String FIELD_COMMANDS_LIST = "l";

    // ValueManager
    public static final String FIELD_PROPERTY_MAP = "a";

    // Module (myau.HT)
    public static final String FIELD_MODULE_NAME = "b";
    public static final String FIELD_MODULE_ENABLED = "X";
    public static final String FIELD_MODULE_KEYBIND = "g";
    public static final String METHOD_MODULE_GET_SUFFIX = "G";

    // Command (myau.Q)
    public static final String FIELD_COMMAND_NAMES = "A";

    // HUD (myau.H_)
    public static final String FIELD_HUD_COLOR_MODE = "a";
    public static final String FIELD_HUD_CUSTOM1 = "k";
    public static final String FIELD_HUD_CUSTOM2 = "V";
    public static final String FIELD_HUD_CUSTOM3 = "L";
    public static final String FIELD_HUD_COLOR_SPEED = "I";
    public static final String FIELD_HUD_COLOR_SATURATION = "Q";
    public static final String FIELD_HUD_COLOR_BRIGHTNESS = "f";
    public static final String FIELD_HUD_SCALE = "w";

    // BedESP (myau.Hd)
    public static final String FIELD_BED_ESP_BEDS = "W";
    public static final String FIELD_BED_ESP_COLOR = "c";
    public static final String FIELD_BED_ESP_MODE = "Z";
    public static final String METHOD_BED_ESP_ON_RENDER_3D = "L"; // old b(myau.mj)V → L(myau.n)V

    // RotationUtil (myau.Hh)
    public static final String CLASS_ROTATION_UTIL = "myau.Hh";
    public static final String METHOD_GET_ROTATIONS_TO_BOX = "y";
    public static final String METHOD_GET_ROTATIONS = "O";

    // RotationState (myau.HX)
    public static final String CLASS_ROTATION_STATE = "myau.HX";
    public static final String FIELD_ROTATION_STATE_PITCH = "c";
    public static final String FIELD_ROTATION_STATE_YAW_HEAD = "V";
    public static final String FIELD_ROTATION_STATE_SMOOTH_YAW = "I";
    public static final String FIELD_ROTATION_STATE_PRIORITY = "H";
    public static final String METHOD_ROTATION_STATE_APPLY = "q";
    public static final String METHOD_ROTATION_STATE_IS_ACTIVE = "P";
    public static final String METHOD_ROTATION_STATE_GET_SMOOTHED_YAW = "c"; // same name as pitch field (field vs method)

    // MoveUtil (myau.HV)
    public static final String CLASS_MOVE_UTIL = "myau.HV";
    public static final String METHOD_MOVE_UTIL_IS_FORWARD_PRESSED = "w";
    public static final String METHOD_MOVE_UTIL_FIX_STRAFE = "j";

    // FastPlace (myau.H7)
    public static final String METHOD_FAST_PLACE_CAN_PLACE = "e";      // old b(III)Z → native e(J)Z
    public static final String SIG_FAST_PLACE_CAN_PLACE = "(J)Z";      // native — coords packed into long
    public static final String METHOD_FAST_PLACE_ON_TICK = "e";        // old F(myau.KP)V → e(myau.V)V — same name as canPlace (overloaded)
    public static final String SIG_FAST_PLACE_ON_TICK = "(Lmyau/V;)V";

    // NoSlow (myau.ZM)
    public static final String METHOD_NO_SLOW_CHECK = "L";             // old r(J)Z → L()Z — long trash removed
    public static final String SIG_NO_SLOW_CHECK = "()Z";              // was (J)Z

    // SwapItemEvent (myau.F)
    public static final String METHOD_SWAP_ITEM_SET_SLOT = "P";

    // AimAssist (myau.HJ)
    public static final String FIELD_AIM_ASSIST_WEAPON_ONLY = "V";
    public static final String FIELD_AIM_ASSIST_ALLOW_TOOLS = "o";
    public static final String FIELD_AIM_ASSIST_RANGE = "M";
    public static final String FIELD_AIM_ASSIST_HSPEED = "n";
    public static final String FIELD_AIM_ASSIST_VSPEED = "B";
    public static final String FIELD_AIM_ASSIST_SMOOTHING = "L";
    public static final String FIELD_AIM_ASSIST_FOV = "N";
    public static final String FIELD_AIM_ASSIST_TEAM = "W";
    public static final String FIELD_AIM_ASSIST_BOT_CHECKS = "e";
    public static final String FIELD_AIM_ASSIST_MC = "x";
    public static final String METHOD_AIM_ASSIST_ON_TICK = "g";           // takes TickEvent POST (myau.b), not PRE — sig: (Lmyau/b;)V
    public static final String SIG_AIM_ASSIST_ON_TICK = "(Lmyau/b;)V";    // NOTE: TickEvent POST, not myau.V (PRE)
    public static final String METHOD_AIM_ASSIST_IS_VALID_TARGET = "G";
    public static final String METHOD_AIM_ASSIST_IS_IN_REACH = "R";
    public static final String METHOD_AIM_ASSIST_IS_LOOKING_AT_BLOCK = "X";

    // KillAura (myau.HD)
    public static final String FIELD_KILL_AURA_TARGET = "t";
    public static final String FIELD_KILL_AURA_ATTACK_RANGE = "ws";
    public static final String FIELD_KILL_AURA_SWING_RANGE = "Z";
    public static final String FIELD_KILL_AURA_FOV = "wy";
    public static final String FIELD_KILL_AURA_SMOOTHING = "W";
    public static final String FIELD_KILL_AURA_DEBUG_LOG = "T"; // unchanged — ModeValue with 2 options
    public static final String FIELD_KILL_AURA_SORT = "m";
    public static final String FIELD_KILL_AURA_ROTATIONS = "G";
    public static final String FIELD_KILL_AURA_MC = "r";
    public static final String FIELD_KILL_AURA_MODE = "o";
    public static final String FIELD_KILL_AURA_PLAYERS = "l";
    public static final String FIELD_KILL_AURA_MOBS = "d";
    public static final String FIELD_KILL_AURA_ANIMALS = "L";
    public static final String FIELD_KILL_AURA_TEAMS = "O";
    public static final String FIELD_KILL_AURA_BOT_CHECK = "i";
    public static final String FIELD_KILL_AURA_WEAPONS_ONLY = "V";
    public static final String FIELD_KILL_AURA_ALLOW_TOOLS = "z";
    public static final String METHOD_KILL_AURA_ON_UPDATE = "j";
    public static final String METHOD_KILL_AURA_ON_TICK = "Q";
    public static final String METHOD_KILL_AURA_PERFORM_ATTACK = "Z";
    public static final String METHOD_KILL_AURA_INTERACT_ATTACK = "Y";
    public static final String METHOD_KILL_AURA_GET_TARGET = "R";
    public static final String METHOD_KILL_AURA_IS_VALID_TARGET = "v"; // unchanged — BUT still has long ZKM trash param
    public static final String SIG_KILL_AURA_IS_VALID_TARGET = "(Lnet/minecraft/entity/EntityLivingBase;J)Z"; // long param NOT removed for this method
    public static final String METHOD_KILL_AURA_IS_PLAYER_TARGET = "X";

    // Value base (myau.H1)
    public static final String FIELD_VALUE_CURRENT = "F";
    public static final String FIELD_VALUE_NAME = "O";
    public static final String FIELD_VALUE_OWNER = "Q";
    public static final String FIELD_VALUE_DEFAULT = "V";
    public static final String FIELD_VALUE_VISIBILITY = "E";

    // RangedValue (myau.H3) — extends H1<Integer>
    public static final String FIELD_RANGED_MIN = "k";
    public static final String FIELD_RANGED_MAX = "C";

    // FloatValue (myau.HA) — extends H1<Float>
    public static final String FIELD_FLOAT_MIN = "R";
    public static final String FIELD_FLOAT_MAX = "Z";

    // IntValue (myau.HY) — extends H1<Integer>
    public static final String FIELD_INT_MIN = "z";
    public static final String FIELD_INT_MAX = "D";

    // EventTyped — REMOVED in 260313 (events split into separate classes)
    // These fields no longer exist; event type is determined by class identity.
    // public static final String FIELD_EVENT_TYPED_PRE = "PRE";
    // public static final String FIELD_EVENT_TYPED_POST = "POST";
    // public static final String FIELD_EVENT_TYPED_SEND = "SEND";

    // EventCancellable (myau.I)
    public static final String FIELD_EVENT_CANCELLED = "l";

    // TickEvent (myau.V) — PRE variant; no type field (events split)
    // public static final String FIELD_TICK_EVENT_TYPE = "p"; // REMOVED — use CLASS_TICK_EVENT / CLASS_TICK_EVENT_POST

    // UpdateEvent (myau.E) — PRE variant
    // public static final String FIELD_UPDATE_EVENT_TYPE = "C"; // REMOVED — use CLASS_UPDATE_EVENT / CLASS_UPDATE_EVENT_POST

    // PacketEvent receive (myau.R)
    public static final String FIELD_PACKET_EVENT_PACKET = "M";
    // public static final String FIELD_PACKET_EVENT_TYPE = "M"; // REMOVED — use CLASS_PACKET_EVENT / CLASS_PACKET_EVENT_SEND
    // PacketEvent send (myau.t) — packet field is "s"
    public static final String FIELD_PACKET_EVENT_SEND_PACKET = "s"; // NEW: packet field in send variant

    // WindowClickEvent (myau.l)
    public static final String FIELD_WINDOW_CLICK_MODE = "C";
    public static final String FIELD_WINDOW_CLICK_BUTTON = "n";
    public static final String FIELD_WINDOW_CLICK_SLOT = "M";

    // ModeValue (myau.Hq)
    public static final String FIELD_ENUM_VALUES_ARRAY = "y";

    // Module methods (myau.HT)
    public static final String METHOD_GET_NAME = "K";
    public static final String METHOD_ON_ENABLE = "a";
    public static final String METHOD_ON_DISABLE = "s";
    public static final String METHOD_SET_ENABLED = "y"; // overloaded: y() = isEnabled, y(boolean) = setEnabled

    // Value methods (myau.H1)
    public static final String METHOD_PROPERTY_GET_NAME = "E";
    public static final String METHOD_PROPERTY_GET_VALUE = "y";
    public static final String METHOD_PROPERTY_SET_OWNER = "L";

    // Command (myau.Q)
    public static final String METHOD_COMMAND_RUN = "j";

    // Event bus (myau.H2) — NOTE: changed from static to instance method
    public static final String METHOD_EVENT_REGISTER = "X";

    // ALL METHOD SIGS — Most ZKM trash parameters removed in 260313
    public static final String SIG_MODULE_CONSTRUCTOR = "(Ljava/lang/String;ZI)V"; // was (Ljava/lang/String;ZIJ)V
    public static final String SIG_ON_ENABLE = "()V"; // was (SIC)V
    public static final String SIG_ON_DISABLE = "()V"; // was (J)V
    public static final String SIG_SET_ENABLED = "(Z)V"; // was (SZSI)V
    public static final String SIG_COMMAND_RUN = "(Ljava/util/ArrayList;)V"; // was (Ljava/util/ArrayList;J)V
    public static final String SIG_COMMAND_CONSTRUCTOR = "(Ljava/util/ArrayList;)V"; // unchanged

    // RotationState method signatures
    public static final String SIG_ROTATION_STATE_IS_ACTIVE = "()Z"; // was (SIS)Z
    public static final String SIG_MOVE_UTIL_IS_FORWARD_PRESSED = "()Z"; // was (CII)Z
    public static final String SIG_MOVE_UTIL_FIX_STRAFE = "(F)V"; // was (JF)V

    // RotationUtil method signatures
    public static final String SIG_GET_ROTATIONS_TO_BOX = "(Lnet/minecraft/util/AxisAlignedBB;)[F"; // was (Lnet/minecraft/util/AxisAlignedBB;JFFFF)[F
    public static final String TARGET_GET_ROTATIONS_TO_BOX = "Lmyau/Hh;y(Lnet/minecraft/util/AxisAlignedBB;)[F";

    // UpdateEvent (myau.E) - rotation methods
    public static final String METHOD_UPDATE_EVENT_SET_ROTATION = "O";
    public static final String SIG_UPDATE_EVENT_SET_ROTATION = "(FFI)V"; // unchanged
    public static final String TARGET_UPDATE_EVENT_SET_ROTATION = "Lmyau/E;O(FFI)V";
    public static final String METHOD_UPDATE_EVENT_GET_YAW = "u";
    public static final String METHOD_UPDATE_EVENT_GET_PITCH = "r";

    // AttackData (myau.Z5)
    public static final String CLASS_ATTACK_DATA = "myau.Z5";
    public static final String FIELD_ATTACK_DATA_BOX = "d"; // now public final — no getter needed
    public static final String METHOD_ATTACK_DATA_GET_BOX = "d"; // field is public; direct access via .d

    public static final String GENERATED_PACKAGE = "coffee.axle.suim.generated.";
    public static final String GENERATED_PACKAGE_INTERNAL = "coffee/axle/suim/generated/";
}
