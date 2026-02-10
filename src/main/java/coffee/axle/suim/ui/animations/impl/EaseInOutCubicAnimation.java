package coffee.axle.suim.ui.animations.impl;

import coffee.axle.suim.ui.animations.Animation;

public class EaseInOutCubicAnimation extends Animation<Double> {

    public EaseInOutCubicAnimation(long duration) {
        super(duration);
    }

    @Override
    public Double get(Double start, Double end, boolean reverse) {
        if (!isAnimating())
            return reverse ? start : end;
        if (reverse) {
            return end + (start - end) * easeInOutCubic();
        } else {
            return start + (end - start) * easeInOutCubic();
        }
    }

    private float easeInOutCubic() {
        float x = getPercent() / 100f;
        if (x < 0.5f) {
            return 4 * x * x * x;
        } else {
            float t = -2 * x + 2;
            return 1 - (t * t * t) / 2;
        }
    }
}





