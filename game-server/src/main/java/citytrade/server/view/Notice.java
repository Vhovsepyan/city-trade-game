package citytrade.server.view;

import java.util.Objects;

/**
 * One client-safe {@link NoticeEvent} addressed to one seat, after {@link NoticeProjector} decided it may
 * see it and stripped whatever must stay private.
 */
public record Notice(int seat, NoticeEvent event) {

    public Notice {
        Objects.requireNonNull(event);
    }
}
