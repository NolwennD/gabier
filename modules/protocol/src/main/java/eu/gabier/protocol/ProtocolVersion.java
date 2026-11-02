package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;

public record ProtocolVersion(int major, int minor) {

    public static final ProtocolVersion CURRENT = new ProtocolVersion(0, 1);

    public ProtocolVersion {
        Assert.field("major", major).positive();
        Assert.field("minor", minor).positive();
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
