package coffee.axle.suim.ui.animations.impl;

import coffee.axle.suim.ui.animations.Animation;

public class LinearAnimation extends Animation<Number> {

    public LinearAnimation(long duration) {
        super(duration);
    }

    @Override
    public Number get(Number start, Number end, boolean reverse) {
        float startVal = reverse ? end.floatValue() : start.floatValue();
        float endVal = reverse ? start.floatValue() : end.floatValue();
        if (!isAnimating())
            return reverse ? start : end;
        return startVal + (endVal - startVal) * (getPercent() / 100f);
    }
}





