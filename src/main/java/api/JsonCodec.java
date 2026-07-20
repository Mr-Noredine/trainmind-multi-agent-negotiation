import java.util.Collection;

public final class JsonCodec {
    private JsonCodec() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String stringArray(Collection<String> values) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(escape(value)).append("\"");
            first = false;
        }
        json.append("]");
        return json.toString();
    }
}
