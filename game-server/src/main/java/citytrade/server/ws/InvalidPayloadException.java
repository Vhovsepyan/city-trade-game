package citytrade.server.ws;

/** {@link ClientCommands#toCommand}: the payload does not match that command type's shape. */
final class InvalidPayloadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
