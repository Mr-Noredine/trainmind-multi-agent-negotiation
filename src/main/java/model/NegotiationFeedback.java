import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NegotiationFeedback {
    public int round;
    public boolean accepted;
    public String riskLevel;
    public int riskScore;
    public final List<String> constraints = new ArrayList<>();
    public final List<String> arguments = new ArrayList<>();

    public String serialize() {
        return "round=" + round
                + "\naccepted=" + accepted
                + "\nrisk_level=" + riskLevel
                + "\nrisk_score=" + riskScore
                + "\nconstraints=" + String.join(";", constraints)
                + "\narguments=" + String.join(";", arguments);
    }

    public static NegotiationFeedback parse(String content) {
        NegotiationFeedback feedback = new NegotiationFeedback();
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String[] pair = line.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim();
            String value = pair[1].trim();
            if ("round".equals(key)) {
                feedback.round = parseInt(value);
            } else if ("accepted".equals(key)) {
                feedback.accepted = Boolean.parseBoolean(value);
            } else if ("risk_level".equals(key)) {
                feedback.riskLevel = value;
            } else if ("risk_score".equals(key)) {
                feedback.riskScore = parseInt(value);
            } else if ("constraints".equals(key)) {
                addList(feedback.constraints, value);
            } else if ("arguments".equals(key)) {
                addList(feedback.arguments, value);
            }
        }
        return feedback;
    }

    private static void addList(List<String> list, String value) {
        if (!value.isEmpty()) {
            list.addAll(Arrays.asList(value.split(";")));
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
