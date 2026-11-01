package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CapabilitiesTest {

    @ParameterizedTest
    @EnumSource(Command.class)
    void everyCommandHasTheCapabilityThatRunsIt(Command command) {
        assertThat(Capabilities.of(command).command()).isEqualTo(command);
    }
}
