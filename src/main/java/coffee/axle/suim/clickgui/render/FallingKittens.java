package coffee.axle.suim.clickgui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FallingKittens {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String RESOURCE_DOMAIN = "suim";

    private final List<Kitten> kittens;

    public FallingKittens() {
        kittens = new ArrayList<>(150);
        for (int i = 0; i < 150; i++) {
            kittens.add(new Kitten());
        }
    }

    public void drawKittens(String type, int size, float speedMultiplier) {
        for (Kitten kitten : kittens) {
            kitten.update(speedMultiplier);
            kitten.draw(type, size);
        }
    }

    public static class Kitten {
        private static final Random random = new Random();

        private float x;
        private float y;
        private final float speed;
        private float rotation;
        private final float rotationSpeed;
        private long lastUpdateTime;

        public Kitten() {
            speed = random.nextFloat() * 2 + 1;
            rotation = random.nextFloat() * 360;
            rotationSpeed = random.nextFloat() * 2 - 1;
            lastUpdateTime = System.nanoTime();

            ScaledResolution sr = new ScaledResolution(mc);
            x = random.nextFloat() * sr.getScaledWidth();
            y = random.nextFloat() * sr.getScaledHeight();
        }

        public void update(float speedMultiplier) {
            long currentTime = System.nanoTime();
            float deltaTime = (currentTime - lastUpdateTime) / 10_000_000.0f;
            ScaledResolution sr = new ScaledResolution(mc);

            if (deltaTime > 250) {
                resetPosition(sr, false);
                deltaTime = 0f;
            }

            lastUpdateTime = currentTime;

            y += speed * deltaTime * 0.25f * speedMultiplier;
            rotation += rotationSpeed * deltaTime * 0.2f;

            if (y - 50 > sr.getScaledHeight()) {
                resetPosition(sr, true);
            }
        }

        public void draw(String type, int size) {
            ResourceLocation texture = new ResourceLocation(RESOURCE_DOMAIN, "fallingkittens/" + type);
            float offset = (float) size / 2;

            GlStateManager.pushMatrix();
            GlStateManager.translate(x - offset, y - offset, 0f);
            GlStateManager.rotate(rotation, 0f, 0f, 1f);
            GlStateManager.translate(-offset, -offset, 0f);
            mc.getTextureManager().bindTexture(texture);
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 0f, 0f, size, size, (float) size, (float) size);
            GlStateManager.popMatrix();
        }

        private void resetPosition(ScaledResolution sr, boolean isOffscreen) {
            x = random.nextFloat() * sr.getScaledWidth();
            y = isOffscreen ? -15f : random.nextFloat() * sr.getScaledHeight();
        }
    }
}





