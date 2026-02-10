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

    /**
     * Convenience method returning an int result.
     */
    public int getInt(int start, int end, boolean reverse) {
        return get(start, end, reverse).intValue();
    }

    /**
     * Convenience method returning a float result.
     */
    public float getFloat(float start, float end, boolean reverse) {
        return get(start, end, reverse).floatValue();
    }
}





