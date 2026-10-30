package fr.craft.gabier.daemon;

import fr.craft.gabier.protocol.JsonRpc;
import fr.craft.gabier.shared.error.domain.GeneratorException;

/** A request does not carry the parameters its method takes: the problem object names the parameter. */
final class ParameterException extends GeneratorException {

    private ParameterException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** The request carries a parameter the method does not take. */
    static ParameterException unknown(String method, String parameter) {
        return new ParameterException(GeneratorException.badRequest(DaemonErrorKey.UNKNOWN_PARAMETER)
                .message("The method " + method + " takes no parameter " + parameter)
                .addParameter(JsonRpc.PARAMETER, parameter));
    }

    /** The request carries a parameter whose value cannot mean what the method needs (a path that is not one). */
    static ParameterException invalid(String method, String parameter) {
        return new ParameterException(GeneratorException.badRequest(DaemonErrorKey.INVALID_PARAMETER)
                .message("The parameter " + parameter + " of the method " + method + " has an invalid value")
                .addParameter(JsonRpc.PARAMETER, parameter));
    }

    /** The request lacks a parameter the method needs. */
    static ParameterException missing(String method, String parameter) {
        return new ParameterException(GeneratorException.badRequest(DaemonErrorKey.MISSING_PARAMETER)
                .message("The method " + method + " needs the parameter " + parameter)
                .addParameter(JsonRpc.PARAMETER, parameter));
    }
}
