package citytrade.server.room;

@FunctionalInterface
public interface TokenGenerator {

    String nextToken();
}
