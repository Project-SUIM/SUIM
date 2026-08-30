package coffee.axle.suim.feature.world;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// AutoDef DefenseData port: preset patterns, block names match bed_defenses.json
final class DefData {
    static final String[] NAMES = {
            "Wool1Layer", "EndWoolCorners", "EndWoolCorners2", "EndGlassNoCorners",
            "WoodWoolCorners", "EndGlassWool", "EndWoodLongCorners", "EndWoolLong"
    };

    static final class Ent {
        final String block;
        final int x, y, z;

        Ent(String block, int x, int y, int z) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final List<List<Ent>> DEFS = Arrays.asList(
            def(e("wool", -1, 0, 0), e("wool", -1, 0, 1), e("wool", 0, 0, 2), e("wool", 1, 0, 0),
                    e("wool", 1, 0, 1), e("wool", 0, 1, 1), e("wool", 0, 1, 0), e("wool", 0, 0, -1)),
            def(e("end_stone", 0, 0, 2), e("wool", 0, 0, 3), e("wool", 0, 0, -2), e("end_stone", 0, 0, -1),
                    e("wool", 1, 0, -1), e("wool", 1, 1, -1), e("wool", 0, 1, -1), e("wool", -1, 0, -1),
                    e("wool", -1, 1, -1), e("end_stone", -2, 0, 0), e("end_stone", -2, 0, 1), e("end_stone", -1, 0, 0),
                    e("end_stone", -1, 0, 1), e("wool", -1, 1, 0), e("wool", -1, 1, 1), e("wool", -1, 0, 2),
                    e("end_stone", 2, 0, 0), e("end_stone", 2, 0, 1), e("end_stone", 1, 0, 0), e("end_stone", 1, 0, 1),
                    e("wool", 1, 1, 0), e("wool", 1, 1, 1), e("wool", 1, 0, 2), e("end_stone", 0, 1, 0),
                    e("end_stone", 0, 1, 1), e("wool", 0, 2, 0), e("wool", 0, 2, 1), e("wool", -1, 1, 2),
                    e("wool", 1, 1, 2), e("wool", 0, 1, 2)),
            def(e("end_stone", -2, 0, 0), e("end_stone", -2, 0, 1), e("end_stone", -1, 0, 0), e("end_stone", -1, 0, 1),
                    e("end_stone", 0, 0, 2), e("end_stone", 1, 0, 0), e("end_stone", 1, 0, 1), e("end_stone", 2, 0, 0),
                    e("end_stone", 2, 0, 1), e("end_stone", 0, 1, 1), e("end_stone", 0, 1, 0), e("end_stone", 0, 0, -1),
                    e("wool", 1, 0, -1), e("wool", 0, 1, -1), e("wool", 0, 0, -2), e("wool", -1, 0, -1),
                    e("wool", 1, 1, 1), e("wool", 1, 1, 0), e("wool", 1, 1, -1), e("wool", 0, 2, 1),
                    e("wool", 0, 2, 0), e("wool", -1, 1, 1), e("wool", -1, 1, 0), e("wool", -1, 1, -1),
                    e("wool", -1, 0, 2), e("wool", -1, 1, 2), e("wool", 0, 1, 2), e("wool", 1, 1, 2),
                    e("wool", 1, 0, 2), e("wool", 0, 0, 3)),
            def(e("end_stone", 0, 0, 2), e("stained_glass", 0, 0, 3), e("stained_glass", -1, 0, -1),
                    e("stained_glass", 0, 0, -2), e("stained_glass", 1, 0, -1), e("end_stone", 0, 0, -1),
                    e("end_stone", -2, 0, 0), e("end_stone", -2, 0, 1), e("end_stone", -1, 0, 0),
                    e("end_stone", -1, 0, 1), e("end_stone", 2, 0, 0), e("end_stone", 2, 0, 1),
                    e("end_stone", 1, 0, 0), e("end_stone", 1, 0, 1), e("stained_glass", -1, 1, 0),
                    e("stained_glass", -1, 1, 1), e("stained_glass", -1, 0, 2), e("stained_glass", 0, 1, -1),
                    e("stained_glass", 1, 1, 0), e("stained_glass", 1, 1, 1), e("stained_glass", 1, 0, 2),
                    e("end_stone", 0, 1, 0), e("end_stone", 0, 1, 1), e("stained_glass", 0, 2, 0),
                    e("stained_glass", 0, 2, 1), e("stained_glass", 0, 1, 2)),
            def(e("planks", -1, 0, -1), e("planks", -1, 0, 0), e("planks", -1, 0, 1), e("planks", -1, 0, 2),
                    e("planks", 1, 0, -1), e("planks", 1, 0, 0), e("planks", 1, 0, 1), e("planks", 1, 0, 2),
                    e("planks", 0, 0, 2), e("planks", 0, 0, 3), e("planks", 0, 0, -2), e("planks", 0, 0, -1),
                    e("planks", 0, 1, -1), e("planks", 0, 1, 0), e("planks", 0, 1, 1), e("planks", 0, 1, 2),
                    e("wool", -2, 0, 0), e("wool", -2, 0, 1), e("wool", -1, 1, -1), e("wool", -1, 1, 0),
                    e("wool", -1, 1, 1), e("wool", -1, 1, 2), e("wool", 0, 2, 0), e("wool", 0, 2, 1),
                    e("wool", 1, 1, -1), e("wool", 1, 1, 0), e("wool", 1, 1, 1), e("wool", 1, 1, 2),
                    e("wool", 2, 0, 0), e("wool", 2, 0, 1)),
            def(e("end_stone", -2, 0, 0), e("end_stone", -2, 0, 1), e("end_stone", -1, 0, 0), e("end_stone", -1, 0, 1),
                    e("end_stone", 0, 0, 2), e("end_stone", 1, 0, 0), e("end_stone", 1, 0, 1), e("end_stone", 2, 0, 0),
                    e("end_stone", 2, 0, 1), e("end_stone", 0, 1, 1), e("end_stone", 0, 1, 0), e("end_stone", 0, 0, -1),
                    e("stained_glass", 1, 0, -1), e("stained_glass", 0, 1, -1), e("stained_glass", 0, 0, -2),
                    e("stained_glass", -1, 0, -1), e("stained_glass", 1, 1, 1), e("stained_glass", 1, 1, 0),
                    e("stained_glass", 0, 2, 1), e("stained_glass", 0, 2, 0), e("stained_glass", -1, 1, 1),
                    e("stained_glass", -1, 1, 0), e("stained_glass", -1, 0, 2), e("stained_glass", 0, 1, 2),
                    e("stained_glass", 1, 0, 2), e("stained_glass", 0, 0, 3), e("wool", -3, 0, 0), e("wool", -3, 0, 1),
                    e("wool", -2, 0, 2), e("wool", -2, 1, 0), e("wool", -2, 1, 1), e("wool", -1, 1, 2),
                    e("wool", -1, 0, 3), e("wool", 0, 1, 3), e("wool", 0, 0, 4), e("wool", 1, 0, 3),
                    e("wool", 1, 1, 2), e("wool", 2, 1, 0), e("wool", 2, 1, 1), e("wool", 2, 0, 2),
                    e("wool", 3, 0, 0), e("wool", 3, 0, 1), e("wool", 1, 2, 0), e("wool", 1, 2, 1),
                    e("wool", 0, 2, 2), e("wool", -1, 2, 0), e("wool", -1, 2, 1), e("wool", -2, 0, -1),
                    e("wool", -1, 0, -2), e("wool", 0, 0, -3), e("wool", 1, 0, -2), e("wool", 2, 0, -1),
                    e("wool", 1, 1, -1), e("wool", 0, 1, -2)),
            def(e("end_stone", 0, 0, 2), e("end_stone", -1, 0, -1), e("end_stone", -1, 0, 0), e("end_stone", -1, 0, 1),
                    e("end_stone", 0, 0, -2), e("end_stone", 0, 0, -1), e("end_stone", 1, 0, -1), e("end_stone", 1, 0, 0),
                    e("end_stone", 1, 0, 1), e("end_stone", 0, 1, -1), e("end_stone", 0, 1, 0), e("end_stone", 0, 1, 1),
                    e("planks", -2, 0, 0), e("planks", -2, 0, 1), e("planks", -1, 1, -1), e("planks", -1, 1, 0),
                    e("planks", -1, 1, 1), e("planks", 0, 2, 0), e("planks", 0, 2, 1), e("planks", 1, 1, -1),
                    e("planks", 1, 1, 0), e("planks", 1, 1, 1), e("planks", 2, 0, 0), e("planks", 2, 0, 1),
                    e("planks", 1, 0, 2), e("planks", 0, 1, 2), e("planks", 0, 0, 3), e("planks", -1, 0, 2)),
            def(e("end_stone", 0, 0, 2), e("end_stone", -1, 0, -1), e("end_stone", -1, 0, 0), e("end_stone", -1, 0, 1),
                    e("end_stone", 0, 0, -2), e("end_stone", 0, 0, -1), e("end_stone", 1, 0, -1), e("end_stone", 1, 0, 0),
                    e("end_stone", 1, 0, 1), e("end_stone", 0, 1, -1), e("end_stone", 0, 1, 0), e("end_stone", 0, 1, 1),
                    e("wool", -2, 0, -1), e("wool", -2, 0, 0), e("wool", -2, 0, 1), e("wool", -1, 1, -1),
                    e("wool", -1, 1, 0), e("wool", -1, 1, 1), e("wool", 0, 2, -1), e("wool", 0, 2, 0),
                    e("wool", 0, 2, 1), e("wool", 1, 1, -1), e("wool", 1, 1, 0), e("wool", 1, 1, 1),
                    e("wool", 2, 0, -1), e("wool", 2, 0, 0), e("wool", 2, 0, 1), e("wool", -1, 0, 2),
                    e("wool", 0, 1, 2), e("wool", 0, 0, 3), e("wool", 1, 0, 2), e("wool", 1, 0, -2),
                    e("wool", 0, 1, -2), e("wool", 0, 0, -3), e("wool", -1, 0, -2))
    );

    private static List<Ent> def(Ent... ents) {
        return Collections.unmodifiableList(Arrays.asList(ents));
    }

    private static Ent e(String block, int x, int y, int z) {
        return new Ent(block, x, y, z);
    }

    static List<Ent> get(int idx) {
        return DEFS.get(idx);
    }

    static int idx(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) return i;
        }
        return 0;
    }
}
