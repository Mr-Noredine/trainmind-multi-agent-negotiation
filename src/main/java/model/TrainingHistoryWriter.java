import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;

public class TrainingHistoryWriter {
    private static final String DEFAULT_HISTORY_PATH = "src/main/data/training_history.csv";
    private static final String HEADER = "date,target,category,exercise,load_kg,reps,rpe,distance_km";
    private final String historyPath;

    public TrainingHistoryWriter() {
        this(DEFAULT_HISTORY_PATH);
    }

    public TrainingHistoryWriter(String historyPath) {
        this.historyPath = historyPath;
    }

    public void appendEntry(
            LocalDate date,
            String target,
            String category,
            String exercise,
            double loadKg,
            int reps,
            int rpe,
            double distanceKm
    ) throws IOException {
        File file = new File(historyPath);
        boolean needsHeader = !file.exists() || file.length() == 0;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            if (needsHeader) {
                writer.write(HEADER);
                writer.newLine();
            }
            writer.write(formatDate(date));
            writer.write(",");
            writer.write(normalize(target));
            writer.write(",");
            writer.write(normalize(category));
            writer.write(",");
            writer.write(normalize(exercise));
            writer.write(",");
            writer.write(formatDouble(loadKg));
            writer.write(",");
            writer.write(Integer.toString(Math.max(0, reps)));
            writer.write(",");
            writer.write(Integer.toString(clamp(rpe, 0, 10)));
            writer.write(",");
            writer.write(formatDouble(distanceKm));
            writer.newLine();
        }
    }

    private String formatDate(LocalDate date) {
        return date == null ? LocalDate.now().toString() : date.toString();
    }

    private String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "none";
        }
        return raw.trim().toLowerCase().replace("-", "_").replace(" ", "_");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatDouble(double value) {
        double rounded = Math.round(Math.max(0, value) * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Integer.toString((int) rounded);
        }
        return Double.toString(rounded);
    }
}
