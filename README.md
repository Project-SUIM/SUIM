# SUIM - Société des Utilitaires et Injections pour Myau

"claude decompile this bih"

A feature-rich injection mod for Myau client (1.8.9 Forge). Adds standalone modules, property injections into existing Myau modules, custom commands, and more. all via dreadful reflection and mixins.

[Join the discord](https://discord.gg/4umJGqwftc) for support, configs, and updates.
Or contact @axle.coffee on discord

> You need **PAID Myau** client for this. Only works with Myau-250910 this will **not work with** OpenMyau.

---

## Features

### Combat

- **MoreKB** Sprint-reset knockback enhancement (Legit, Packet, Double Packet modes + intelligent targeting)
- **HitSelect** Filters outgoing attack packets for optimal hit timing (Second, Criticals, WTap modes)
- **MultiPointAiming**  Dynamic multi-point vertical targeting for AimAssist
- **ArmorExceptions** Prevent KillAura from targeting players wearing specified armor
- **AutoClicker Extras** Inventory-fill (auto-steal from chests) and require-press options
- **KillAura DisableOnDeath** Auto-disables KillAura on death screen

### Render

- **Freelook** Third-person camera free-look with toggle/hold modes and custom FOV
- **BedPlates** Floating billboards above beds showing defense layers (block analysis)
- **SkullESP** Highlights player skull tile entities with outline boxes and tracers
- **AimAssist ShowTarget** Visual indicator around AimAssist target (HUD/Box modes)
- **BedESP TeamColor** Colors BedESP highlights by Hypixel Bedwars team using map data
- **Xray SpawnersNametags** Floating name tags above mob spawners showing entity type

### World

- **AutoBlockIn** Automatic cage/block-in builder with smooth rotation and smart bed targeting
- **AutoClutch** Automatic fall-clutch via Scaffold with configurable triggers and delays
- **Eagle AutoSwap** Auto-swap to fullest block stack when current stack runs out
- **FastPlace Extras** Skip obsidian and skip interactable block options

### Player

- **InvManager Extras** Drop-trash exception list and drop-tools option

### Exploit

- **Freeze** Freezes all player motion entirely, stores and restores on disable
- **ShopBlink** blinka bw shopa (aha)
- **BufferVelocity** Buffers incoming velocity/teleport packets with tick timer HUD

### Commands

- `.dmyau` / `.myau` Help menu listing all d'Myau features by category
- `.find` / `.locate`  Search loaded chunks for closest matching block
- `.status <player>` Fetch Hypixel player online status and recent games
- `.client` / `.info` Display client info; change watermark name

### Other

- **Custom ClickGUI** with blur, custom font, and autosave
- **Batch `.friend` and `.enemy` commands** Add/remove multiple players at once
- **KillAura Sword NoSlow bypass** via slot-switch packets
- **Anti-detection** Removes mod from FML handshake mod list
- **Packet event system** Send/Receive packet events on the Forge event bus

---

## Credits

- **maybsomeday** for figuring out the proper Myau fields and methods to inject into + sharing a (PRIVATE) mappings file!!!
- Codebase based on **nea89's** Forge 1.8.9 template (with Kotlin) (they're very cool)

## Also check out

[CoffeeClient](https://github.com/axlecoffee/CoffeeClient) Which is where shimmed OpenMyau (ish) into Lunar Client using NotEnoughUpdates

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
