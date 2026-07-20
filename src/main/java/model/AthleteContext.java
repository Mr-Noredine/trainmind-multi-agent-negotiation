import java.util.LinkedHashMap;
import java.util.Map;

public class AthleteContext {
    private final Map<String, String> values = new LinkedHashMap<>();

    public static AthleteContext parse(String raw) {
        AthleteContext context = new AthleteContext();
        if (raw == null) {
            return context.withDefaults();
        }

        String cleaned = raw.trim();
        if (cleaned.startsWith("{")) {
            cleaned = cleaned.replace("{", "").replace("}", "").replace("\"", "");
        }

        String[] parts = cleaned.split(",");
        for (String part : parts) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            String[] keyValue = item.split("=", 2);
            if (keyValue.length == 2) {
                context.values.put(normalize(keyValue[0]), keyValue[1].trim().toLowerCase());
            } else {
                context.values.put(normalize(item), "true");
            }
        }
        return context.withDefaults();
    }

    private AthleteContext withDefaults() {
        putDefault("goal", "running");
        putDefault("cycle_phase", "accumulation");
        putDefault("sleep_hours", "7.5");
        putDefault("fatigue", "4");
        putDefault("soreness", "3");
        putDefault("soreness_zone", "none");
        putDefault("joint_pain", "0");
        putDefault("joint_pain_zone", "none");
        putDefault("hrv", "normal");
        putDefault("acwr", "1.0");
        putDefault("last_push_days", "3");
        putDefault("last_pull_days", "4");
        putDefault("last_legs_days", "5");
        putDefault("last_run_km", "6");
        putDefault("weekly_km", "20");
        putDefault("chronic_km", "22");
        putDefault("max_hr", "190");
        putDefault("rest_hr", "55");
        putDefault("vma_kmh", "14");
        putDefault("preferred_sport", "auto");
        putDefault("sessions_per_week", "4");
        putDefault("available_days", "4");
        putDefault("available_minutes", "60");
        putDefault("equipment", "full_gym");
        putDefault("preferences", "none");
        putDefault("injuries", "none");
        putDefault("forbidden_exercises", "none");
        putDefault("last_load_kg", "60");
        putDefault("last_reps", "8");
        putDefault("last_rpe", "7");
        putDefault("training_age", "intermediate");
        return this;
    }

    private void putDefault(String key, String value) {
        if (!values.containsKey(key)) {
            values.put(key, value);
        }
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(get(key, Double.toString(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String freshMuscleTarget() {
        int push = getInt("last_push_days", 3);
        int pull = getInt("last_pull_days", 4);
        int legs = getInt("last_legs_days", 5);
        if (legs >= push && legs >= pull) {
            return "legs";
        }
        if (pull >= push) {
            return "pull";
        }
        return "push";
    }

    public String saferAlternativeTarget(String currentTarget) {
        String[] targets = {"legs", "pull", "push"};
        String sorenessZone = get("soreness_zone", "none");
        String best = currentTarget;
        int bestDays = -1;
        for (String target : targets) {
            if (target.equals(currentTarget) || target.equals(sorenessZone) || targetConflictsWithPain(target)) {
                continue;
            }
            int days = getInt("last_" + target + "_days", 0);
            if (days > bestDays) {
                best = target;
                bestDays = days;
            }
        }
        return best;
    }

    private boolean targetConflictsWithPain(String target) {
        String painZone = get("joint_pain_zone", "none");
        boolean activePain = getInt("joint_pain", 0) >= 4;
        boolean shoulderRisk = activePain && "shoulder".equals(painZone) || hasToken("injuries", "shoulder");
        boolean backRisk = activePain && "back".equals(painZone) || hasToken("injuries", "back");
        boolean kneeRisk = activePain && "knee".equals(painZone) || hasToken("injuries", "knee");
        boolean ankleRisk = activePain && "ankle".equals(painZone) || hasToken("injuries", "ankle");
        boolean elbowRisk = activePain && "elbow".equals(painZone) || hasToken("injuries", "elbow");
        boolean wristRisk = activePain && "wrist".equals(painZone) || hasToken("injuries", "wrist");

        if ("legs".equals(target)) {
            return backRisk || kneeRisk || ankleRisk;
        }
        if ("pull".equals(target)) {
            return shoulderRisk || backRisk || elbowRisk;
        }
        if ("push".equals(target)) {
            return shoulderRisk || elbowRisk || wristRisk;
        }
        return false;
    }

    public boolean hasToken(String key, String token) {
        String raw = get(key, "none");
        if (raw == null || token == null) {
            return false;
        }
        String normalizedToken = normalize(token);
        String[] parts = raw.split("[|;/]");
        for (String part : parts) {
            if (normalize(part).equals(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyToken(String key, String... tokens) {
        for (String token : tokens) {
            if (hasToken(key, token)) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si l'historique contient une donnee reelle pour cet exercice (pas juste le fallback). */
    public boolean hasExerciseHistory(String exerciseId) {
        return values.containsKey(normalize(exerciseId) + "_load");
    }

    public double getExerciseLoad(String exerciseId) {
        return getDouble(normalize(exerciseId) + "_load", getDouble("last_load_kg", 60));
    }

    public int getExerciseReps(String exerciseId) {
        return getInt(normalize(exerciseId) + "_reps", getInt("last_reps", 8));
    }

    public int getExerciseRpe(String exerciseId) {
        return getInt(normalize(exerciseId) + "_rpe", getInt("last_rpe", 7));
    }

    public String toTraceString() {
        return values.toString();
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase().replace("-", "_").replace(" ", "_");
    }
}
