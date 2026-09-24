package citytrade.server.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import citytrade.server.rest.RoomRequests.Bot;
import citytrade.server.rest.RoomRequests.Nickname;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** MockMvc coverage of the REST contract for T19 (rooms and lobby). */
@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void createReturnsSeatZeroAndToken() throws Exception {
        mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Nickname("host"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seat").value(0))
                .andExpect(jsonPath("$.roomCode").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void joinFillsSeatsOneToThreeThenFifthJoinIsRoomFull() throws Exception {
        RoomCreationResponse host = create("host");
        join(host.roomCode(), "second");
        join(host.roomCode(), "third");
        join(host.roomCode(), "fourth");

        mockMvc.perform(post("/rooms/" + host.roomCode() + "/join").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Nickname("fifth"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_FULL"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unknownRoomCodeReturnsRoomNotFound() throws Exception {
        mockMvc.perform(get("/rooms/ZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void joiningAnActiveRoomIsRoomNotJoinable() throws Exception {
        RoomCreationResponse host = create("host");
        join(host.roomCode(), "second");
        join(host.roomCode(), "third");
        join(host.roomCode(), "fourth");
        startRoom(host.roomCode(), host.token());

        mockMvc.perform(post("/rooms/" + host.roomCode() + "/join").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Nickname("late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_JOINABLE"));
    }

    @Test
    void onlyHostCanStartAddBotsOrRemoveSeats() throws Exception {
        RoomCreationResponse host = create("host");
        join(host.roomCode(), "second");

        mockMvc.perform(post("/rooms/" + host.roomCode() + "/bots").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer wrong-token")
                        .content(json.writeValueAsString(new Bot("BASELINE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"));

        mockMvc.perform(delete("/rooms/" + host.roomCode() + "/seats/1")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"));

        mockMvc.perform(post("/rooms/" + host.roomCode() + "/start")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"));
    }

    @Test
    void startWithFewerThanFourSeatsIsRoomNotFull() throws Exception {
        RoomCreationResponse host = create("host");

        mockMvc.perform(post("/rooms/" + host.roomCode() + "/start")
                        .header("Authorization", "Bearer " + host.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FULL"));
    }

    @Test
    void botsCanOnlyBeAddedOrRemovedInLobby() throws Exception {
        RoomCreationResponse host = create("host");
        join(host.roomCode(), "second");
        join(host.roomCode(), "third");
        join(host.roomCode(), "fourth");
        startRoom(host.roomCode(), host.token());

        mockMvc.perform(post("/rooms/" + host.roomCode() + "/bots").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + host.token())
                        .content(json.writeValueAsString(new Bot("BASELINE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_IN_LOBBY"));

        mockMvc.perform(delete("/rooms/" + host.roomCode() + "/seats/1")
                        .header("Authorization", "Bearer " + host.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_IN_LOBBY"));
    }

    @Test
    void getNeverExposesTokenOrSeed() throws Exception {
        RoomCreationResponse host = create("host");

        MvcResult result = mockMvc.perform(get("/rooms/" + host.roomCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(host.token());
        assertThat(body).doesNotContain("\"token\"");
        assertThat(body).doesNotContain("\"seed\"");
    }

    private RoomCreationResponse create(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Nickname(nickname))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readValue(result.getResponse().getContentAsString(), RoomCreationResponse.class);
    }

    private void join(String code, String nickname) throws Exception {
        mockMvc.perform(post("/rooms/" + code + "/join").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Nickname(nickname))))
                .andExpect(status().isOk());
    }

    private void startRoom(String code, String hostToken) throws Exception {
        mockMvc.perform(post("/rooms/" + code + "/start")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
    }

    private record RoomCreationResponse(String roomCode, int seat, String token) {
    }
}
