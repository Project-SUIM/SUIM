package coffee.axle.suim.ui.animations.impl;

import coffee.axle.suim.ui.animations.Animation;

public class EaseOutQuadAnimation extends Animation<Double> {

    public EaseOutQuadAnimation(long duration) {
        super(duration);
    }

    @Override
    public Double get(Double start, Double end, boolean reverse) {
        double startVal = reverse ? end : start;
        double endVal = reverse ? start : end;
        if (!isAnimating())
            return endVal;
        return startVal + (endVal - startVal) * easeOutQuad();
    }

    private float easeOutQuad() {
        float p = getPercent() / 100f;
        return 1 - (1 - p) * (1 - p);
    }
}





