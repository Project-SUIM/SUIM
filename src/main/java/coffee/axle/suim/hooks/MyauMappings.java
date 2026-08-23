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
 * <strong>Migration notes (260317 -> 260729):</strong><br>
 * - ALL 215 class names re-obfuscated (208 previous + 7 new)<br>
 * - Key hierarchy shifts: gJ->F (Client), gj->V (ModuleBase), h->kr (EventBus),
 *   g->k8 (EventTarget), i->kO (MethodData), g6->kf (ChatUtil), J->kN (PacketUtil)<br>
 * - NEW classes: Jo=HitSelect (module), kR=PacketEventDelayed (Velocity DELAY),
 *   kE=BlockHitManager (auto-block), f=AutoCompleteUtil (chat completion),
 *   z=AngleUtil (static math)<br>
 * - Events re-mapped: Render2D=kx, Render3D=k9, Tick=JS, Update=kQ,
 *   PacketRecv=Jt, PacketSend=Ju, Key=JH, HitBlock=kC, LeftClick=J0,
 *   CancelUse=k1, MoveInput=ko, LoadWorld=kc, PlayerUpdate=Jq, Resize=k2,
 *   LivingUpdate=k7, UpdatePOST=Ji, TickPOST=ky<br>
 * - IMPORTANT: module tick handlers moved off the tick events in 260729 -
 *   AimAssist handles LivingUpdateEvent (k7), FastPlace handles HitBlockEvent
 *   (kC), RotationManager handles HitBlockEvent (kC) + Render3D (k9). Verified
 *   against the raw jar.<br>
 * - KillAura native PERFORM_ATTACK sig changed from (FJF)Z to (JFF)Z (param
 *   reorder); native set grew 8->10 (new auto-block natives)<br>
 * - KillAura methods (resolved 260729): onTick(TickEvent JS)=E, onUpdate(UpdateEvent
 *   kQ)=U, getTarget()=n, isValidTarget=B(J,EntityLivingBase)Z,
 *   isPlayerTarget=v(EntityLivingBase,J)Z, isPlayerBlocking=p(),
 *   unblock=T()V native, interactAttack=j(FJF)V (best guess)<br>
 * - RotationManager HitBlockEvent handler = a(kC) (mapping "E" was the 317 name);
 *   MoveUtil fixStrafe=S(F)V; LagManager isLagging=z()Z<br>
 * - Value types: T=Value, v=Bool, R=Ranged, g=Int, E=Float, s=Text, x=Mode, I=Color<br>
 * - Client fields (jar-verified): rotationManager=l, lagManager=m, eventBus=C,
 *   moduleManager=W, commandManager=w, valueManager=p, friendManager=r,
 *   targetManager=N, clientName=X<br>
 * - Module (jar-verified): getName=E, isEnabled=X, setEnabled=v(Z)V,
 *   onEnabled=Z(), onDisabled=f()<br>
 * - 32 anti-leak protected classes (up from 31 in 260317)<br>
 * - antileak UUID: antiLeak-1a2786b1-7d43-4a96-b4bc-752eb89b1600<br>
 * </p>
 *
 * @author axlecoffee
 * @author ksyz (Myau Client)
 * @author maybesomeday and others (Mappings)
 * @version 260729 (2025-07-29)
 */
public class MyauMappings {
    public static final String CLASS_MAIN = "myau.F";
    public static final String CLASS_MODULE_BASE = "myau.V";
    public static final String CLASS_EVENT_BUS = "myau.kr";
    public static final String CLASS_EVENT_ANNOTATION = "myau.k8";
    public static final String CLASS_HANDLER_WRAPPER = "myau.kO";
    public static final String CLASS_MODULE_COMMAND = "myau.ka";

    public static final String CLASS_EVIL_AUTUMN = "myau.ks"; // please dm foxgirlfrot for information regarding this
                                                              // class - UNVERIFIED for 260729, may have changed

    // Module classes
    public static final String CLASS_HUD = "myau.JW";
    public static final String CLASS_AIM_ASSIST = "myau.G";
    public static final String CLASS_BED_ESP = "myau.C";
    public static final String CLASS_BED_TRACKER = "myau.Z";
    public static final String CLASS_KILL_AURA = "myau.Jh";
    public static final String CLASS_NO_SLOW = "myau.Jd";
    public static final String CLASS_FAST_PLACE = "myau.JQ";

    // Command classes
    public static final String CLASS_TARGET_COMMAND = "myau.ke";
    public static final String CLASS_FRIEND_COMMAND = "myau.kn";

    // Utility classes
    public static final String CLASS_CHAT_UTIL = "myau.kf";
    public static final String CLASS_PACKET_UTIL = "myau.kN";

    // Manager classes
    public static final String CLASS_MODULE_MANAGER = "myau.kw";
    public static final String CLASS_COMMAND_MANAGER = "myau.t";
    public static final String CLASS_VALUE_MANAGER = "myau.kV";
    public static final String CLASS_ROTATION_MANAGER = "myau.kj";

    // LagManager (myau.p)
    public static final String CLASS_LAG_MANAGER = "myau.p";
    public static final String FIELD_LAG_MANAGER = "m"; // Client.lagManager field (jar-verified)
    public static final String FIELD_LAG_MGR_DELAY_TICKS = "F";
    public static final String FIELD_LAG_MGR_IS_LAGGING = "u";
    public static final String METHOD_LAG_MGR_IS_LAGGING = "z"; // isLagging()Z - jar-verified (mapping labeled it "flushing")

    // LagRange module (myau.Jl)
    public static final String CLASS_LAG_RANGE = "myau.Jl";
    public static final String FIELD_LAG_RANGE_TICK_COUNT = "U";
    public static final String FIELD_LAG_RANGE_ACCUMULATOR = "D";
    public static final String FIELD_LAG_RANGE_ACTIVE = "S";
    public static final String FIELD_LAG_RANGE_DELAY_VALUE = "e";

    // DelayManager (myau.kF)
    public static final String CLASS_DELAY_MANAGER = "myau.kF";

    // RotationManager (myau.kj) - all jar-verified
    public static final String FIELD_ROTATION_MANAGER = "l"; // Client.rotationManager field (jar-verified)
    public static final String FIELD_ROT_MGR_YAW_DELTA = "f";
    public static final String FIELD_ROT_MGR_PITCH_DELTA = "I";
    public static final String FIELD_ROT_MGR_ROTATED = "N";
    public static final String FIELD_ROT_MGR_LAST_UPDATE = "L";
    public static final String FIELD_ROT_MGR_PRIORITY = "w";
    public static final String METHOD_ROT_MGR_SET_ROTATION = "W";
    public static final String SIG_ROT_MGR_SET_ROTATION = "(FFIZ)V"; // unchanged
    public static final String METHOD_ROT_MGR_IS_ROTATED = "l";
    public static final String METHOD_ROT_MGR_ON_TICK = "a"; // HitBlockEvent handler (Lmyau/kC;)V - jar-verified ("E" was the 317 name)
    public static final String METHOD_ROT_MGR_RESET = "o"; // overloaded with setRotation(J)V - sig: (J)V

    // Property / Value classes - constructor sigs unchanged from 260313
    public static final String CLASS_VALUE_BASE = "myau.T";
    public static final String CLASS_BOOLEAN_PROPERTY = "myau.v";
    public static final String CLASS_INTEGER_PROPERTY = "myau.R";
    public static final String CLASS_INT_VALUE = "myau.g";
    public static final String CLASS_FLOAT_PROPERTY = "myau.E";
    public static final String CLASS_STRING_PROPERTY = "myau.s";
    public static final String CLASS_ENUM_PROPERTY = "myau.x";
    public static final String CLASS_COLOR_PROPERTY = "myau.I";

    // Property constructor signatures - unchanged from 260313
    // BooleanProperty: (String, Boolean)
    public static final String SIG_BOOLEAN_PROPERTY = "(Ljava/lang/String;Ljava/lang/Boolean;)V";
    public static final String SIG_BOOLEAN_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Boolean;Ljava/util/function/BooleanSupplier;)V";
    // FloatProperty: (String, Float, Float, Float)
    public static final String SIG_FLOAT_PROPERTY = "(Ljava/lang/String;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/Float;)V";
    public static final String SIG_FLOAT_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Float;Ljava/lang/Float;Ljava/lang/Float;Ljava/util/function/BooleanSupplier;)V";
    // EnumProperty: (String, Integer, String[])
    public static final String SIG_ENUM_PROPERTY = "(Ljava/lang/String;Ljava/lang/Integer;[Ljava/lang/String;)V";
    public static final String SIG_ENUM_PROPERTY_VIS = "(Ljava/lang/String;Ljava/lang/Integer;[Ljava/lang/String;Ljava/util/function/BooleanSupplier;)V";
    // RangedValue(myau.R): (String, Integer, Integer, Integer) - unchanged
    public static final String SIG_RANGED_VALUE = "(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;)V";
    // ColorProperty(myau.I): (String, Integer) - unchanged
    public static final String SIG_COLOR_PROPERTY = "(Ljava/lang/String;Ljava/lang/Integer;)V";

    // Event base classes
    public static final String CLASS_EVENT_CANCELLABLE = "myau.J4";

    // Event classes
    // TickEvent: myau.JS (pre) + myau.ky (post)
    public static final String CLASS_TICK_EVENT = "myau.JS";           // PRE only
    public static final String CLASS_TICK_EVENT_POST = "myau.ky";      // POST variant
    // UpdateEvent: myau.kQ (pre) + myau.Ji (post)
    public static final String CLASS_UPDATE_EVENT = "myau.kQ";         // PRE only
    public static final String CLASS_UPDATE_EVENT_POST = "myau.Ji";    // POST variant
    // PacketEvent: myau.Jt (receive) + myau.Ju (send)
    public static final String CLASS_PACKET_EVENT = "myau.Jt";         // RECEIVE only
    public static final String CLASS_PACKET_EVENT_SEND = "myau.Ju";    // SEND variant
    public static final String CLASS_RENDER_2D_EVENT = "myau.kx";
    public static final String CLASS_RENDER_3D_EVENT = "myau.k9";
    public static final String CLASS_WINDOW_CLICK_EVENT = "myau.JU";
    public static final String CLASS_MOVE_INPUT_EVENT = "myau.ko";
    public static final String CLASS_SWAP_ITEM_EVENT = "myau.Jk";
    // NEW in 260729 - module tick handlers moved to these events (jar-verified)
    public static final String CLASS_HIT_BLOCK_EVENT = "myau.kC";
    public static final String CLASS_LIVING_UPDATE_EVENT = "myau.k7";

    // myau.F (Client) - all jar-verified
    public static final String FIELD_MODULE_MANAGER = "W";
    public static final String FIELD_COMMAND_MANAGER = "w";
    public static final String FIELD_PROPERTY_MANAGER = "p";
    public static final String FIELD_CLIENT_NAME = "X";
    public static final String FIELD_FRIEND_MANAGER = "r";
    public static final String FIELD_TARGET_MANAGER = "N";
    public static final String FIELD_EVENT_BUS = "C"; // EventBus instance field in Client

    // EventBus handler internals (myau.kr + myau.kO)
    public static final String FIELD_EVENT_BUS_HANDLER_MAP = "s";
    public static final String METHOD_HANDLER_GET_HANDLER = "s";

    // ChatUtil (myau.kf) - all methods are static
    public static final String METHOD_CHAT_SEND_FORMATTED = "U";
    public static final String SIG_CHAT_SEND_FORMATTED = "(Ljava/lang/String;)V";
    public static final String METHOD_CHAT_SEND_RAW = "T";
    public static final String SIG_CHAT_SEND_RAW = "(Ljava/lang/String;)V";
    public static final String METHOD_CHAT_SEND = "E";
    public static final String SIG_CHAT_SEND = "(Lnet/minecraft/util/IChatComponent;)V";

    // PlayerFileManager superclass fields (base class: myau.H) - all jar-verified
    public static final String FIELD_PLAYER_LIST = "k";
    public static final String FIELD_PLAYER_FILE = "c";
    public static final String METHOD_PLAYER_LIST_CONTAINS = "o";

    // PacketUtil (myau.kN)
    public static final String METHOD_SEND_PACKET = "D";

    // ModuleManager (myau.kw)
    public static final String METHOD_GET_MODULE = "p"; // native getModule(String) - jar-verified
    public static final String METHOD_IS_ENABLED = "X";

    // KillAura (myau.Jh) - mixin-specific fields (jar-verified)
    public static final String FIELD_KILL_AURA_AUTOBLOCK = "T";
    public static final String FIELD_KILL_AURA_IS_BLOCKING = "u";
    public static final String FIELD_KILL_AURA_TICK_STATE = "x";
    public static final String METHOD_KILL_AURA_IS_PLAYER_BLOCKING = "p"; // ()Z - returns isBlocking field (u); jar-verified
    public static final String SIG_KILL_AURA_IS_PLAYER_BLOCKING = "()Z"; // unchanged
    public static final String METHOD_KILL_AURA_UNBLOCK = "T";           // native ()V; jar-verified
    public static final String SIG_KILL_AURA_UNBLOCK = "()V";            // unchanged

    // ModuleManager / CommandManager / ValueManager map fields - jar-verified
    public static final String FIELD_MODULES_MAP = "t";
    public static final String FIELD_COMMANDS_LIST = "A";
    public static final String FIELD_PROPERTY_MAP = "r";

    // Module (myau.V) - jar-verified
    public static final String FIELD_MODULE_NAME = "r";
    public static final String FIELD_MODULE_ENABLED = "a";
    public static final String FIELD_MODULE_KEYBIND = "y";
    public static final String METHOD_MODULE_GET_SUFFIX = "r";

    // Command (myau.kd)
    public static final String FIELD_COMMAND_NAMES = "Z";

    // HUD (myau.JW)
    public static final String FIELD_HUD_COLOR_MODE = "T";
    public static final String FIELD_HUD_CUSTOM1 = "C";
    public static final String FIELD_HUD_CUSTOM2 = "N";
    public static final String FIELD_HUD_CUSTOM3 = "z";
    public static final String FIELD_HUD_COLOR_SPEED = "p";
    public static final String FIELD_HUD_COLOR_SATURATION = "f";
    public static final String FIELD_HUD_COLOR_BRIGHTNESS = "e";
    public static final String FIELD_HUD_SCALE = "W";

    // BedESP (myau.C) - jar-verified
    public static final String FIELD_BED_ESP_BEDS = "p";
    public static final String FIELD_BED_ESP_COLOR = "z";
    public static final String FIELD_BED_ESP_MODE = "P";
    public static final String METHOD_BED_ESP_ON_RENDER_3D = "j"; // sig: (Lmyau/k9;)V - RENDER_3D is myau.k9

    // RotationUtil (myau.kB) - jar-verified
    public static final String CLASS_ROTATION_UTIL = "myau.kB";
    public static final String METHOD_GET_ROTATIONS_TO_BOX = "B";
    public static final String METHOD_GET_ROTATIONS = "a";

    // RotationState (myau.K) - jar-verified
    public static final String CLASS_ROTATION_STATE = "myau.K";
    public static final String FIELD_ROTATION_STATE_PITCH = "n";
    public static final String FIELD_ROTATION_STATE_YAW_HEAD = "b";
    public static final String FIELD_ROTATION_STATE_SMOOTH_YAW = "c";
    public static final String FIELD_ROTATION_STATE_PRIORITY = "V";
    public static final String METHOD_ROTATION_STATE_APPLY = "z"; // applyState(ZFFFI)V
    public static final String METHOD_ROTATION_STATE_IS_ACTIVE = "P";
    public static final String METHOD_ROTATION_STATE_GET_SMOOTHED_YAW = "o";

    // MoveUtil (myau.kz)
    public static final String CLASS_MOVE_UTIL = "myau.kz";
    public static final String METHOD_MOVE_UTIL_IS_FORWARD_PRESSED = "H";
    public static final String METHOD_MOVE_UTIL_FIX_STRAFE = "S"; // (F)V - jar-verified (mapping "I" was s()I)

    // NEW in 260729
    public static final String CLASS_HIT_SELECT = "myau.Jo";          // new module
    public static final String CLASS_PACKET_EVENT_DELAYED = "myau.kR"; // used by Velocity DELAY
    public static final String CLASS_BLOCK_HIT_MANAGER = "myau.kE";    // auto-block timing
    public static final String CLASS_AUTO_COMPLETE_UTIL = "myau.f";    // chat auto-completion
    public static final String CLASS_ANGLE_UTIL = "myau.z";            // static angle/math utils

    // FastPlace (myau.JQ) - jar-verified: onTick takes HitBlockEvent (kC)
    public static final String METHOD_FAST_PLACE_CAN_PLACE = "C";      // native - sig: (J)Z
    public static final String SIG_FAST_PLACE_CAN_PLACE = "(J)Z";      // unchanged
    public static final String METHOD_FAST_PLACE_ON_TICK = "e";
    public static final String SIG_FAST_PLACE_ON_TICK = "(Lmyau/kC;)V"; // HitBlockEvent in 260729

    // NoSlow (myau.Jd)
    public static final String METHOD_NO_SLOW_CHECK = "L";
    public static final String SIG_NO_SLOW_CHECK = "()Z";              // unchanged

    // SwapItemEvent (myau.Jk)
    public static final String METHOD_SWAP_ITEM_SET_SLOT = "G";

    // AimAssist (myau.G) - all jar-verified; onTick takes LivingUpdateEvent (k7)
    public static final String FIELD_AIM_ASSIST_WEAPON_ONLY = "h";
    public static final String FIELD_AIM_ASSIST_ALLOW_TOOLS = "f";
    public static final String FIELD_AIM_ASSIST_RANGE = "l";
    public static final String FIELD_AIM_ASSIST_HSPEED = "L";
    public static final String FIELD_AIM_ASSIST_VSPEED = "u";
    public static final String FIELD_AIM_ASSIST_SMOOTHING = "O";
    public static final String FIELD_AIM_ASSIST_FOV = "e";
    public static final String FIELD_AIM_ASSIST_BOT_CHECKS = "k"; // gates kY.w (isBot) in s() - deobf-confirmed; mapping had W
    public static final String FIELD_AIM_ASSIST_TEAM = "s";       // gates kY.Q (isTeam) in s() - bytecode-verified; mapping had x
    public static final String FIELD_AIM_ASSIST_MC = "v";
    // G.P / G.S: the float[2] smoothing integrators (P[0]=yaw,P[1]=pitch target
    // state, S[0]/S[1]=current smoothed angles) from decompiling myau.G 260729
    public static final String FIELD_AIM_ASSIST_SMOOTH_STATE_YAW = "P";
    public static final String FIELD_AIM_ASSIST_SMOOTH_STATE_PITCH = "S";
    public static final String METHOD_AIM_ASSIST_ON_TICK = "e";           // takes LivingUpdateEvent (k7) in 260729
    public static final String SIG_AIM_ASSIST_ON_TICK = "(Lmyau/k7;)V";   // NOTE: LivingUpdateEvent, not TickEvent POST
    public static final String METHOD_AIM_ASSIST_IS_VALID_TARGET = "t";
    public static final String METHOD_AIM_ASSIST_IS_IN_REACH = "r"; // 260729 fallback - folded into native j(long)
    public static final String METHOD_AIM_ASSIST_IS_LOOKING_AT_BLOCK = "j";

    // TeamUtil (myau.kY) - public static bot/team checks shared by AimAssist, KillAura, Tracers, NameTags
    public static final String CLASS_TEAM_UTIL = "myau.kY";
    public static final String METHOD_TEAM_UTIL_IS_BOT = "w";  // (EntityPlayer)Z - Hypixel NPC detection (UUID v2, ping, name marker)
    public static final String METHOD_TEAM_UTIL_IS_TEAM = "Q"; // (EntityPlayer)Z - scoreboard team check

    // KillAura (myau.Jh) - fields + methods jar-verified (interactAttack = best guess)
    public static final String FIELD_KILL_AURA_TARGET = "c";
    public static final String FIELD_KILL_AURA_ATTACK_RANGE = "j";
    public static final String FIELD_KILL_AURA_SWING_RANGE = "Q";
    public static final String FIELD_KILL_AURA_FOV = "Tz";
    public static final String FIELD_KILL_AURA_SMOOTHING = "o";
    public static final String FIELD_KILL_AURA_DEBUG_LOG = "J";
    public static final String FIELD_KILL_AURA_SORT = "T2";
    public static final String FIELD_KILL_AURA_ROTATIONS = "TU";
    public static final String FIELD_KILL_AURA_MC = "K";
    public static final String FIELD_KILL_AURA_MODE = "Tg";
    public static final String FIELD_KILL_AURA_PLAYERS = "S";
    public static final String FIELD_KILL_AURA_MOBS = "Z";
    public static final String FIELD_KILL_AURA_ANIMALS = "U";
    public static final String FIELD_KILL_AURA_TEAMS = "d";
    public static final String FIELD_KILL_AURA_BOT_CHECK = "m";
    public static final String FIELD_KILL_AURA_WEAPONS_ONLY = "TI";
    public static final String FIELD_KILL_AURA_ALLOW_TOOLS = "e";
    public static final String METHOD_KILL_AURA_ON_UPDATE = "U";      // jar-verified: takes UpdateEvent (kQ)
    public static final String METHOD_KILL_AURA_ON_TICK = "E";         // jar-verified: takes TickEvent (JS)
    public static final String METHOD_KILL_AURA_PERFORM_ATTACK = "f";  // native - sig changed: (JFF)Z (was (FJF)Z - param reorder)
    public static final String METHOD_KILL_AURA_INTERACT_ATTACK = "j"; // native (FJF)V - best guess, flagged
    public static final String METHOD_KILL_AURA_GET_TARGET = "n";      // jar-verified: getTarget()
    public static final String METHOD_KILL_AURA_IS_VALID_TARGET = "B";
    public static final String SIG_KILL_AURA_IS_VALID_TARGET = "(JLnet/minecraft/entity/EntityLivingBase;)Z"; // param order (long, EntityLivingBase)
    public static final String METHOD_KILL_AURA_IS_PLAYER_TARGET = "v"; // (EntityLivingBase,J)Z - EntityPlayer-only check

    // BedBreaker (myau.D) - bed-nuker target + state fields (jar-verified 260729)
    public static final String CLASS_BED_BREAKER = "myau.D";
    public static final String FIELD_BED_BREAKER_TARGET_BED = "p";        // BlockPos current target
    public static final String FIELD_BED_BREAKER_BREAKING = "F";          // boolean - actively breaking
    public static final String FIELD_BED_BREAKER_READY_TO_BREAK = "t";    // boolean - ready to break
    public static final String FIELD_BED_BREAKER_IS_BED = "G";            // boolean - current target is a bed
    public static final String FIELD_BED_BREAKER_WAITING_FOR_START = "J"; // boolean - waiting for start

    // Value base (myau.T) - jar-verified
    public static final String FIELD_VALUE_CURRENT = "Q";
    public static final String FIELD_VALUE_NAME = "W";
    public static final String FIELD_VALUE_OWNER = "m";
    public static final String FIELD_VALUE_DEFAULT = "C";
    public static final String FIELD_VALUE_VISIBILITY = "K";

    // RangedValue (myau.R) - extends T<Integer>
    public static final String FIELD_RANGED_MIN = "O";
    public static final String FIELD_RANGED_MAX = "w";

    // FloatValue (myau.E) - extends T<Float>
    public static final String FIELD_FLOAT_MIN = "n";
    public static final String FIELD_FLOAT_MAX = "Y";

    // IntValue (myau.g) - extends T<Integer>
    public static final String FIELD_INT_MIN = "M";
    public static final String FIELD_INT_MAX = "g";

    // EventCancellable (myau.J4)
    public static final String FIELD_EVENT_CANCELLED = "j";

    // PacketEvent receive (myau.Jt) - packet field jar-verified
    public static final String FIELD_PACKET_EVENT_PACKET = "I";
    // PacketEvent send (myau.Ju) - packet field jar-verified
    public static final String FIELD_PACKET_EVENT_SEND_PACKET = "K";

    // WindowClickEvent (myau.JU) - slot=2nd, button=3rd, mode=4th field; verified
    public static final String FIELD_WINDOW_CLICK_MODE = "M";
    public static final String FIELD_WINDOW_CLICK_BUTTON = "U";
    public static final String FIELD_WINDOW_CLICK_SLOT = "A";

    // ModeValue (myau.x)
    public static final String FIELD_ENUM_VALUES_ARRAY = "P";

    // Module methods (myau.V) - jar-verified: isEnabled=X(), setEnabled=v(Z)V
    public static final String METHOD_GET_NAME = "E";
    public static final String METHOD_ON_ENABLE = "Z";
    public static final String METHOD_ON_DISABLE = "f"; // was "G" in 260317
    public static final String METHOD_SET_ENABLED = "v"; // isEnabled(Z)V - was "G" in 260317

    // Value methods (myau.T) - jar-verified
    public static final String METHOD_PROPERTY_GET_NAME = "M";
    public static final String METHOD_PROPERTY_GET_VALUE = "S";
    public static final String METHOD_PROPERTY_SET_OWNER = "X";
    public static final String METHOD_PROPERTY_SET_VALUE = "f"; // setValue(Object)Z - jar-verified

    // Command (myau.kd)
    public static final String METHOD_COMMAND_RUN = "k";

    // Event bus (myau.kr)
    public static final String METHOD_EVENT_REGISTER = "W";

    // ALL METHOD SIGS - unchanged from 260313 unless noted
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

    // RotationUtil method signatures - jar-verified
    public static final String SIG_GET_ROTATIONS_TO_BOX = "(Lnet/minecraft/util/AxisAlignedBB;)[F"; // unchanged
    public static final String TARGET_GET_ROTATIONS_TO_BOX = "Lmyau/kB;B(Lnet/minecraft/util/AxisAlignedBB;)[F";

    // UpdateEvent (myau.kQ) - rotation methods - jar-verified
    public static final String METHOD_UPDATE_EVENT_SET_ROTATION = "q";
    public static final String SIG_UPDATE_EVENT_SET_ROTATION = "(FFI)V"; // unchanged
    public static final String TARGET_UPDATE_EVENT_SET_ROTATION = "Lmyau/kQ;q(FFI)V";
    public static final String METHOD_UPDATE_EVENT_GET_YAW = "N";
    public static final String METHOD_UPDATE_EVENT_GET_PITCH = "l";

    // AttackData (myau.kv)
    public static final String CLASS_ATTACK_DATA = "myau.kv";
    public static final String FIELD_ATTACK_DATA_BOX = "N"; // public final field
    public static final String METHOD_ATTACK_DATA_GET_BOX = "N"; // field is public; direct access via .N (no getter)

    public static final String GENERATED_PACKAGE = "coffee.axle.suim.generated.";
    public static final String GENERATED_PACKAGE_INTERNAL = "coffee/axle/suim/generated/";
}
