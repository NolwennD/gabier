package fr.craft.gabier.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;

/** A real JSON parser in its strict mode (RFC 8259 only), so that "it parses" means "it is valid JSON". */
final class StrictJson {

    private StrictJson() {}

    static JsonElement parse(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Text after the JSON value: " + json);
            }

            return element;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Not valid JSON: " + json, e);
        }
    }
}
