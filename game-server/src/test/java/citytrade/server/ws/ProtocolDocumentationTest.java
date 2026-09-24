package citytrade.server.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T22 accept criterion: every JSON example in {@code docs/PROTOCOL.md} must actually parse. */
class ProtocolDocumentationTest {

    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\r?\\n(.*?)```", Pattern.DOTALL);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void everyJsonExampleParses() throws IOException {
        String text = Files.readString(protocolMd());
        List<String> blocks = extractJsonBlocks(text);
        assertThat(blocks).hasSizeGreaterThanOrEqualTo(6);
        for (String block : blocks) {
            JsonNode node = JSON.readTree(block);
            assertThat(node.isObject()).as("example is a JSON object:%n%s", block).isTrue();
        }
    }

    @Test
    void everyCommandTypeExampleHasNoSeatField() throws IOException {
        String text = Files.readString(protocolMd());
        for (String block : extractJsonBlocks(text)) {
            JsonNode node = JSON.readTree(block);
            JsonNode payload = node.get("payload");
            if (payload != null && payload.isObject()) {
                assertThat(payload.has("seat")).as("payload must never carry a seat field:%n%s", block).isFalse();
            }
        }
    }

    private static List<String> extractJsonBlocks(String text) {
        Matcher matcher = JSON_BLOCK.matcher(text);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }

    private static Path protocolMd() {
        return Path.of(System.getProperty("docs.dir", "../docs"), "PROTOCOL.md");
    }
}
