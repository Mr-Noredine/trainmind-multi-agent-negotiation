import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RaisonFactMapper {
    private RaisonFactMapper() {}

    public static List<RaisonElementInput> performanceFacts(AthleteContext ctx, boolean conservativeMode) {
        List<RaisonElementInput> facts = new ArrayList<>();
        facts.add(RaisonElementInput.parameter("risk_conservative_mode", "RC", Boolean.toString(conservativeMode)));
        facts.add(RaisonElementInput.parameter("goal", "G", normalize(ctx.get("goal", "running"))));
        facts.add(RaisonElementInput.parameter("cycle_phase", "C", normalize(ctx.get("cycle_phase", "accumulation"))));
        facts.add(RaisonElementInput.parameter("training_age", "T", normalize(ctx.get("training_age", "intermediate"))));
        facts.add(RaisonElementInput.parameter("hrv", "H", normalize(ctx.get("hrv", "normal"))));
        facts.add(RaisonElementInput.parameter("fatigue", "F", Integer.toString(ctx.getInt("fatigue", 4))));
        facts.add(RaisonElementInput.parameter("sleep_hours", "S", formatDouble(ctx.getDouble("sleep_hours", 7.5))));
        facts.add(RaisonElementInput.parameter("joint_pain", "J", Integer.toString(ctx.getInt("joint_pain", 0))));
        facts.add(RaisonElementInput.parameter("soreness", "O", Integer.toString(ctx.getInt("soreness", 3))));
        facts.add(RaisonElementInput.parameter("acwr", "A", formatDouble(computeAcwr(ctx))));
        return facts;
    }

    public static List<RaisonElementInput> recoveryFacts(AthleteContext ctx) {
        List<RaisonElementInput> facts = new ArrayList<>();
        facts.add(RaisonElementInput.parameter("cycle_phase", "C", normalize(ctx.get("cycle_phase", "accumulation"))));
        return facts;
    }

    public static double computeAcwr(AthleteContext ctx) {
        double stored = ctx.getDouble("acwr", 0);
        if (stored > 0) {
            return stored;
        }
        double weekly = ctx.getDouble("weekly_km", 0);
        double chronic = ctx.getDouble("chronic_km", 0);
        if (chronic > 0 && weekly > 0) {
            return weekly / chronic;
        }
        return 1.0;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }
}
