import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrainingProposal {
    public int round;
    public String phase;
    public String category;
    public String target;
    public String intensity;
    public int rpe;
    public int volumePercent;
    public double runningKm;
    public final List<String> exercises = new ArrayList<>();
    public final List<String> arguments = new ArrayList<>();
    public final List<String> constraints = new ArrayList<>();

    public String serialize() {
        return "round=" + round
                + "\nphase=" + safe(phase)
                + "\ncategory=" + safe(category)
                + "\ntarget=" + safe(target)
                + "\nintensity=" + safe(intensity)
                + "\nrpe=" + rpe
                + "\nvolume_percent=" + volumePercent
                + "\nrunning_km=" + runningKm
                + "\nexercises=" + String.join(";", exercises)
                + "\narguments=" + String.join(";", arguments)
                + "\nconstraints=" + String.join(";", constraints);
    }

    public static TrainingProposal parse(String content) {
        TrainingProposal proposal = new TrainingProposal();
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String[] pair = line.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim();
            String value = pair[1].trim();
            if ("round".equals(key)) {
                proposal.round = parseInt(value, 0);
            } else if ("phase".equals(key)) {
                proposal.phase = value;
            } else if ("category".equals(key)) {
                proposal.category = value;
            } else if ("target".equals(key)) {
                proposal.target = value;
            } else if ("intensity".equals(key)) {
                proposal.intensity = value;
            } else if ("rpe".equals(key)) {
                proposal.rpe = parseInt(value, 0);
            } else if ("volume_percent".equals(key)) {
                proposal.volumePercent = parseInt(value, 100);
            } else if ("running_km".equals(key)) {
                proposal.runningKm = parseDouble(value, 0);
            } else if ("exercises".equals(key)) {
                addList(proposal.exercises, value);
            } else if ("arguments".equals(key)) {
                addList(proposal.arguments, value);
            } else if ("constraints".equals(key)) {
                addList(proposal.constraints, value);
            }
        }
        return proposal;
    }

    public TrainingProposal copy() {
        TrainingProposal next = new TrainingProposal();
        next.round = round;
        next.phase = phase;
        next.category = category;
        next.target = target;
        next.intensity = intensity;
        next.rpe = rpe;
        next.volumePercent = volumePercent;
        next.runningKm = runningKm;
        next.exercises.addAll(exercises);
        next.arguments.addAll(arguments);
        next.constraints.addAll(constraints);
        return next;
    }

    private static void addList(List<String> list, String value) {
        if (value.isEmpty()) {
            return;
        }
        list.addAll(Arrays.asList(value.split(";")));
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
