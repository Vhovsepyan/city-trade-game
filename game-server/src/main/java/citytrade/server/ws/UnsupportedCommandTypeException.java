package citytrade.server.ws;

/** {@link ClientCommands#toCommand}: {@code commandType} is not a known player command. */
final class UnsupportedCommandTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnsupportedCommandTypeException(String commandType) {
        super("unsupported commandType: " + commandType);
    }
}
