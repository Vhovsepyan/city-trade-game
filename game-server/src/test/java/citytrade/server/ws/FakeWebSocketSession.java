package citytrade.server.ws;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/** A minimal in-memory {@link WebSocketSession} test double: records every sent text payload. */
final class FakeWebSocketSession implements WebSocketSession {

    private final String id;
    private final List<String> sentMessages = new ArrayList<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private boolean open = true;

    FakeWebSocketSession() {
        this("fake-session-" + System.nanoTime());
    }

    FakeWebSocketSession(String id) {
        this.id = id;
    }

    List<String> sentMessages() {
        return List.copyOf(sentMessages);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getUri() {
        return URI.create("ws://localhost/ws");
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getTextMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) {
        if (message instanceof TextMessage text) {
            sentMessages.add(text.getPayload());
        }
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public void close(CloseStatus status) {
        open = false;
    }
}
