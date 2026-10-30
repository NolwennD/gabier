package fr.craft.gabier.daemon;

/** {@code stop}: the daemon answers, then stops. */
record StopMethod(Runnable stop) implements DaemonMethod {

    @Override
    public String name() {
        return "stop";
    }

    @Override
    public Outcome call() {
        return new Outcome("{\"stopping\":true}", stop);
    }
}
