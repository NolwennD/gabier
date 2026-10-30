package fr.craft.gabier.protocol;

public record ProtocolVersion(int major, int minor) {

    public static final ProtocolVersion CURRENT = new ProtocolVersion(0, 1);

    public ProtocolVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("A protocol version cannot be negative: " + major + "." + minor);
        }
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
