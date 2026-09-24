package citytrade.server.rest;

import citytrade.server.rest.RoomRequests.Bot;
import citytrade.server.rest.RoomRequests.Nickname;
import citytrade.server.room.BotType;
import citytrade.server.room.RoomCreation;
import citytrade.server.room.RoomRegistry;
import citytrade.server.room.RoomSnapshot;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter; room lifecycle rules live in {@link RoomRegistry}. */
@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomRegistry rooms;

    public RoomController(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    public ResponseEntity<RoomCreation> create(@RequestBody Nickname request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rooms.create(request == null ? null : request.nickname()));
    }

    @PostMapping("/{code}/join")
    public RoomCreation join(@PathVariable String code, @RequestBody Nickname request) {
        return rooms.join(code, request == null ? null : request.nickname());
    }

    @GetMapping("/{code}")
    public RoomSnapshot get(@PathVariable String code) {
        return rooms.snapshot(code);
    }

    @PostMapping("/{code}/bots")
    public Map<String, Object> addBot(@PathVariable String code,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Bot request) {
        int seat = rooms.addBot(code, bearerToken(authorization), BotType.parse(request == null ? null : request.type()));
        return Map.of("seat", seat);
    }

    @DeleteMapping("/{code}/seats/{seat}")
    public ResponseEntity<Void> removeSeat(@PathVariable String code,
            @PathVariable int seat,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        rooms.removeSeat(code, bearerToken(authorization), seat);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/start")
    public RoomSnapshot start(@PathVariable String code,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return rooms.start(code, bearerToken(authorization));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
