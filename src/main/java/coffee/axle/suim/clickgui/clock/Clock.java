package coffee.axle.suim.clickgui.clock;

public class Clock {
    private long delay;
    private long lastTime;

    public Clock() {
        this(0L);
    }

    public Clock(long delay) {
        this.delay = delay;
        this.lastTime = System.currentTimeMillis();
    }

    public long getTime() {
        return System.currentTimeMillis() - lastTime;
    }

    public void setTime(long time) {
        this.lastTime = time;
    }

    public void update() {
        this.lastTime = System.currentTimeMillis();
    }

    public void updateCD() {
        this.lastTime = System.currentTimeMillis() + delay;
    }

    public boolean hasTimePassed() {
        return hasTimePassed(false);
    }

    public boolean hasTimePassed(boolean setTime) {
        if (getTime() >= delay) {
            if (setTime)
                lastTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public long timeLeft() {
        return lastTime - System.currentTimeMillis();
    }

    public boolean hasTimePassed(long delay, boolean setTime) {
        if (getTime() >= delay) {
            if (setTime)
                lastTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public long getDelay() {
        return delay;
    }
}





