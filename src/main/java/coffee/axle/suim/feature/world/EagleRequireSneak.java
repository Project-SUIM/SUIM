// package coffee.axle.suim.feature.world;

// import coffee.axle.suim.feature.Feature;
// import coffee.axle.suim.util.MyauLogger;

// // Suggestion: eagle require sneak option
// // @claire .claiire. 08/28/2026

// public class EagleRequireSneak extends Feature {
//     private static EagleRequireSneak instance;

//     private Object eagleModule;
//     private Object requireSneakProperty;

//     @Override
//     public String getName() {
//         return "Eagle:RequireSneak";
//     }

//     @Override
//     public boolean initialize() {
//         try {
//             eagleModule = manager.findModule("Eagle");
//             if (eagleModule == null) {
//                 MyauLogger.log(getName(), "MODULE_NOT_FOUND");
//                 return false;
//             }

//             requireSneakProperty = creator.createBooleanProperty("require-sneak", false);

//             if (!creator.injectProperty(eagleModule, requireSneakProperty)) {
//                 MyauLogger.log(getName(), "PROPERTY_INJECT_FAIL");
//                 return false;
//             }

//             instance = this;

//             return true;

//         } catch (Exception e) {
//             MyauLogger.error("FEATURE_FAIL", e);
//             return false;
//         }
//     }

//     // called from MixinEntityPlayerSP after the k2 dispatch
//     public static boolean shouldCancelSneak() {
//         EagleRequireSneak f = instance;
//         if (f == null)
//             return false;
//         try {
//             if (!f.manager.isModuleEnabled(f.eagleModule))
//                 return false;
//             Boolean requireSneak = (Boolean) f.properties.getPropertyValue(f.requireSneakProperty);
//             return requireSneak != null && requireSneak;
//         } catch (Exception e) {
//             return false;
//         }
//     }
// }
