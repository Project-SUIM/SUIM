package coffee.axle.suim.ui.animations;

import coffee.axle.suim.clickgui.clock.Clock;

public abstract class Animation<T> {
    private final long duration;
    private boolean animating = false;
    private final Clock clock;

    public Animation(long duration) {
        this.duration = duration;
        this.clock = new Clock(duration);
    }

    public boolean start() {
        return start(false);
    }

    public boolean start(boolean bypass) {
        if (!animating || bypass) {
            animating = true;
            clock.update();
            return true;
        }
        return false;
    }

    public int getPercent() {
        if (animating) {
            int percent = (int) (clock.getTime() / (double) duration * 100);
            if (percent > 100) {
                animating = false;
            }
            return percent;
        } else {
            return 100;
        }
    }

    public boolean isAnimating() {
        return animating;
    }

    public abstract T get(T start, T end, boolean reverse);

    public T get(T start, T end) {
        return get(start, end, false);
    }
}





