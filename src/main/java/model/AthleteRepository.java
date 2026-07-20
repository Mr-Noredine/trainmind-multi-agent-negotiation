import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AthleteRepository {
    private static final String DEFAULT_PROFILE_PATH = "src/main/data/athlete_profile.properties";
    private static final String DEFAULT_HISTORY_PATH = "src/main/data/training_history.csv";
    private final String profilePath;
    private final String historyPath;
    private final LocalDate referenceDate;

    public AthleteRepository() {
        this(DEFAULT_PROFILE_PATH, DEFAULT_HISTORY_PATH, LocalDate.now());
    }

    public AthleteRepository(String profilePath) {
        this(profilePath, DEFAULT_HISTORY_PATH, LocalDate.now());
    }

    public AthleteRepository(String profilePath, String historyPath, LocalDate referenceDate) {
        this.profilePath = profilePath;
        this.historyPath = historyPath;
        this.referenceDate = referenceDate;
    }

    public String buildCompleteContext(String dailyContext) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.putAll(loadProfile());
        merged.putAll(loadHistoryContext());
        merged.putAll(parseKeyValueContext(dailyContext));
        return serialize(merged);
    }

    public Map<String, String> loadProfile() {
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(profilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] pair = trimmed.split("=", 2);
                if (pair.length == 2) {
                    values.put(normalize(pair[0]), pair[1].trim().toLowerCase());
                }
            }
        } catch (IOException e) {
            System.out.println("[MANAGER] Profil athlete introuvable, defaults AthleteContext utilises: " + profilePath);
        }
        return values;
    }

    public Map<String, String> loadHistoryContext() {
        Map<String, String> values = new LinkedHashMap<>();
        List<HistoryEntry> entries = loadHistory();
        if (entries.isEmpty()) {
            return values;
        }

        putLastTargetDays(values, entries, "push");
        putLastTargetDays(values, entries, "pull");
        putLastTargetDays(values, entries, "legs");
        putRunningMetrics(values, entries);
        putExerciseMetrics(values, entries);
        return values;
    }

    private List<HistoryEntry> loadHistory() {
        List<HistoryEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(historyPath))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    if (trimmed.toLowerCase().startsWith("date,")) {
                        continue;
                    }
                }
                HistoryEntry entry = HistoryEntry.parse(trimmed);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            System.out.println("[MANAGER] Historique athlete introuvable, profil seul utilise: " + historyPath);
        }
        return entries;
    }

    private void putLastTargetDays(Map<String, String> values, List<HistoryEntry> entries, String target) {
        LocalDate latest = null;
        for (HistoryEntry entry : entries) {
            if (!target.equals(entry.target)) {
                continue;
            }
            if (latest == null || entry.date.isAfter(latest)) {
                latest = entry.date;
            }
        }
        if (latest != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(latest, referenceDate));
            values.put("last_" + target + "_days", Long.toString(days));
        }
    }

    private void putRunningMetrics(Map<String, String> values, List<HistoryEntry> entries) {
        LocalDate latestRun = null;
        double latestDistance = 0;
        double weeklyKm = 0;
        double chronicTotalKm = 0;

        for (HistoryEntry entry : entries) {
            if (!"running".equals(entry.target) || entry.distanceKm <= 0) {
                continue;
            }
            long ageDays = ChronoUnit.DAYS.between(entry.date, referenceDate);
            if (ageDays >= 0 && ageDays <= 7) {
                weeklyKm += entry.distanceKm;
            }
            if (ageDays >= 0 && ageDays <= 28) {
                chronicTotalKm += entry.distanceKm;
            }
            if (latestRun == null || entry.date.isAfter(latestRun)) {
                latestRun = entry.date;
                latestDistance = entry.distanceKm;
            }
        }

        if (latestRun != null) {
            double chronicWeeklyKm = chronicTotalKm / 4.0;
            double minimumChronicFromLatestRun = latestDistance / 0.45;
            chronicWeeklyKm = Math.max(chronicWeeklyKm, minimumChronicFromLatestRun);
            values.put("last_run_km", formatDouble(latestDistance));
            values.put("weekly_km", formatDouble(weeklyKm));
            values.put("chronic_km", formatDouble(chronicWeeklyKm));
        }
    }

    private void putExerciseMetrics(Map<String, String> values, List<HistoryEntry> entries) {
        Map<String, HistoryEntry> latestByExercise = new LinkedHashMap<>();
        for (HistoryEntry entry : entries) {
            if (entry.exercise.isEmpty() || "run".equals(entry.exercise) || entry.loadKg <= 0) {
                continue;
            }
            HistoryEntry previous = latestByExercise.get(entry.exercise);
            if (previous == null || entry.date.isAfter(previous.date)) {
                latestByExercise.put(entry.exercise, entry);
            }
        }

        for (Map.Entry<String, HistoryEntry> item : latestByExercise.entrySet()) {
            String exercise = normalize(item.getKey());
            HistoryEntry entry = item.getValue();
            values.put(exercise + "_load", formatDouble(entry.loadKg));
            values.put(exercise + "_reps", Integer.toString(entry.reps));
            values.put(exercise + "_rpe", Integer.toString(entry.rpe));
        }
    }

    public Map<String, String> parseKeyValueContext(String rawContext) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawContext == null) {
            return values;
        }
        String cleaned = rawContext.trim();
        if (cleaned.startsWith("{")) {
            cleaned = cleaned.replace("{", "").replace("}", "").replace("\"", "");
        }
        String[] parts = cleaned.split(",");
        for (String part : parts) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            String[] pair = item.split("=", 2);
            if (pair.length == 2) {
                values.put(normalize(pair[0]), pair[1].trim().toLowerCase());
            } else {
                values.put(normalize(item), "true");
            }
        }
        return values;
    }

    private String serialize(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private String normalize(String raw) {
        return raw.trim().toLowerCase().replace("-", "_").replace(" ", "_");
    }

    private String formatDouble(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Integer.toString((int) rounded);
        }
        return Double.toString(rounded);
    }

    private static class HistoryEntry {
        final LocalDate date;
        final String target;
        final String category;
        final String exercise;
        final double loadKg;
        final int reps;
        final int rpe;
        final double distanceKm;

        private HistoryEntry(
                LocalDate date,
                String target,
                String category,
                String exercise,
                double loadKg,
                int reps,
                int rpe,
                double distanceKm
        ) {
            this.date = date;
            this.target = target;
            this.category = category;
            this.exercise = exercise;
            this.loadKg = loadKg;
            this.reps = reps;
            this.rpe = rpe;
            this.distanceKm = distanceKm;
        }

        static HistoryEntry parse(String line) {
            String[] columns = line.split(",", -1);
            if (columns.length < 8) {
                return null;
            }
            try {
                return new HistoryEntry(
                        LocalDate.parse(columns[0].trim()),
                        normalizeStatic(columns[1]),
                        normalizeStatic(columns[2]),
                        normalizeStatic(columns[3]),
                        parseDouble(columns[4]),
                        parseInt(columns[5]),
                        parseInt(columns[6]),
                        parseDouble(columns[7])
                );
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static String normalizeStatic(String raw) {
            return raw.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        }

        private static double parseDouble(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return 0;
            }
            return Double.parseDouble(raw.trim());
        }

        private static int parseInt(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return 0;
            }
            return Integer.parseInt(raw.trim());
        }
    }
}
