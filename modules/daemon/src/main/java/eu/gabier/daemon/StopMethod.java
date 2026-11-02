package eu.gabier.daemon;

import java.util.Map;

/** {@code stop}: the daemon answers, then stops. */
record StopMethod(Runnable stop) implements DaemonMethod {

    @Override
    public String name() {
        return "stop";
    }

    @Override
    public Outcome call(Map<String, String> params) {
        return new Outcome("{\"stopping\":true}", stop);
    }
}
