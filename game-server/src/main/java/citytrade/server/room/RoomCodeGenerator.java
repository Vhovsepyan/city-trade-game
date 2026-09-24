package citytrade.server.room;

@FunctionalInterface
public interface RoomCodeGenerator {

    String nextCode();
}
