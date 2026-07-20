import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExerciseLibrary {

    public static class Exercise {
        public final String id;
        public final String name;
        public final String target;
        public final String role;
        public final boolean loadable;
        public final double defaultLoadKg; // poids de depart suggere (debutant conservateur)

        Exercise(String id, String name, String target, String role, boolean loadable, double defaultLoadKg) {
            this.id = id;
            this.name = name;
            this.target = target;
            this.role = role;
            this.loadable = loadable;
            this.defaultLoadKg = defaultLoadKg;
        }
    }

    public List<Exercise> selectStrengthExercises(AthleteContext context, TrainingProposal proposal) {
        List<Exercise> candidates = candidatesFor(context, proposal.target);
        List<Exercise> selected = new ArrayList<>();

        // Sélectionner le meilleur exercice principal selon le contexte et la phase
        Exercise main = selectMain(context, candidates, proposal.category);
        if (main != null) selected.add(main);

        // Complémentaire (secondary) différent du main
        Exercise sec = firstCompatibleExcluding(context, candidates, "secondary", selected);
        if (sec != null) selected.add(sec);

        // Accessoire
        Exercise acc = firstCompatibleExcluding(context, candidates, "accessory", selected);
        if (acc != null) selected.add(acc);

        // Finisseur (isolation/pompante)
        Exercise fin = firstCompatibleExcluding(context, candidates, "finisher", selected);
        if (fin != null) selected.add(fin);

        return selected;
    }

    private Exercise selectMain(AthleteContext context, List<Exercise> candidates, String category) {
        String phase = context.get("cycle_phase", "accumulation");
        boolean isStrength = category != null && category.contains("strength");
        boolean isPower    = category != null && category.contains("power");

        // Pour force/puissance : prioriser squat/bench/overhead press si disponibles
        if (isStrength || isPower) {
            for (Exercise ex : candidates) {
                if ("main".equals(ex.role) && isAllowed(context, ex)
                        && (ex.id.contains("squat") || ex.id.contains("bench")
                            || ex.id.contains("overhead_press") || ex.id.contains("lat_pulldown")
                            || ex.id.contains("barbell_row")))
                    return ex;
            }
        }
        // Pour hypertrophie en intensification : alterner les exercices principaux
        if ("intensification".equals(phase)) {
            for (Exercise ex : candidates) {
                if ("main".equals(ex.role) && isAllowed(context, ex)
                        && (ex.id.contains("overhead") || ex.id.contains("barbell_row")
                            || ex.id.contains("squat")))
                    return ex;
            }
        }
        return firstCompatibleExcluding(context, candidates, "main", new ArrayList<>());
    }

    private Exercise firstCompatibleExcluding(AthleteContext context, List<Exercise> candidates,
                                               String role, List<Exercise> excluded) {
        for (Exercise ex : candidates) {
            if (role.equals(ex.role) && isAllowed(context, ex) && !excluded.contains(ex))
                return ex;
        }
        // fallback sans contraintes de sécurité
        for (Exercise ex : candidates) {
            if (role.equals(ex.role) && !excluded.contains(ex)) return ex;
        }
        return null;
    }

    private List<Exercise> candidatesFor(AthleteContext context, String target) {
        String equipment   = context.get("equipment", "full_gym");
        String preferences = context.get("preferences", "none");
        if ("bodyweight".equals(equipment) || "home".equals(equipment)) return bodyweightCandidates(target);
        if ("dumbbells".equals(equipment))                               return dumbbellCandidates(target);
        if (preferences.contains("machines"))                            return machineCandidates(target);
        return fullGymCandidates(target);
    }



    private boolean isAllowed(AthleteContext context, Exercise exercise) {
        if (context.hasToken("forbidden_exercises", exercise.id)) return false;

        String painZone = context.getInt("joint_pain", 0) >= 4
                ? context.get("joint_pain_zone", "none") : "none";

        if (context.hasToken("injuries", "shoulder") || "shoulder".equals(painZone)) {
            if (exercise.id.contains("bench") || exercise.id.contains("overhead")
                    || exercise.id.contains("lateral_raise") || exercise.id.contains("dip"))
                return false;
        }
        if (context.hasToken("injuries", "back") || "back".equals(painZone)) {
            if (exercise.id.contains("deadlift") || exercise.id.contains("barbell_row")
                    || exercise.id.contains("squat"))
                return false;
        }
        if (context.hasAnyToken("injuries", "knee", "ankle")
                || "knee".equals(painZone) || "ankle".equals(painZone)) {
            if (exercise.id.contains("squat") || exercise.id.contains("lunge")
                    || exercise.id.contains("split_squat") || exercise.id.contains("leg_press"))
                return false;
        }
        return true;
    }

    // ═══ Catalogues d'exercices ══════════════════════════════════════════════
    // Poids par defaut : conservateur (debutant peut les soulever en securite).
    // L'historique remplace ces valeurs des la 2e seance.

    private List<Exercise> fullGymCandidates(String target) {
        if ("push".equals(target)) return Arrays.asList(
            ex("bench_press",              "Bench press",              target, "main",      true,  20.0),
            ex("overhead_press",           "Overhead press",           target, "main",      true,  20.0),
            ex("incline_dumbbell_press",   "Incline dumbbell press",   target, "secondary", true,  10.0),
            ex("cable_fly",                "Cable fly",                target, "secondary", true,   8.0),
            ex("lateral_raise",            "Lateral raise",            target, "accessory", true,   5.0),
            ex("rear_delt_fly",            "Rear delt fly",            target, "accessory", true,   5.0),
            ex("triceps_rope_pushdown",    "Triceps rope pushdown",    target, "finisher",  true,  12.0),
            ex("overhead_triceps_cable",   "Overhead triceps cable",   target, "finisher",  true,  10.0)
        );
        if ("pull".equals(target)) return Arrays.asList(
            ex("lat_pulldown",             "Lat pulldown",             target, "main",      true,  30.0),
            ex("barbell_row",              "Barbell row",              target, "main",      true,  30.0),
            ex("seated_cable_row",         "Seated cable row",         target, "secondary", true,  25.0),
            ex("chest_supported_row",      "Chest-supported row",      target, "secondary", true,  20.0),
            ex("face_pull",                "Face pull",                target, "accessory", true,  10.0),
            ex("incline_dumbbell_curl",    "Incline dumbbell curl",    target, "accessory", true,   8.0),
            ex("dumbbell_curl",            "Dumbbell curl",            target, "finisher",  true,   8.0),
            ex("hammer_curl",              "Hammer curl",              target, "finisher",  true,   8.0)
        );
        // legs (defaut)
        return Arrays.asList(
            ex("squat",                    "Squat",                    target, "main",      true,  40.0),
            ex("leg_press",                "Leg press",                target, "main",      true,  40.0),
            ex("romanian_deadlift",        "Romanian deadlift",        target, "secondary", true,  30.0),
            ex("hip_thrust",               "Hip thrust",               target, "secondary", true,  30.0),
            ex("goblet_squat",             "Goblet squat",             target, "secondary", true,  12.0),
            ex("split_squat",              "Split squat",              target, "accessory", false,  0.0),
            ex("leg_curl",                 "Leg curl",                 target, "accessory", true,  20.0),
            ex("standing_calf_raise",      "Standing calf raise",      target, "finisher",  true,  20.0)
        );
    }

    private List<Exercise> machineCandidates(String target) {
        if ("push".equals(target)) return Arrays.asList(
            ex("machine_chest_press",     "Machine chest press",     target, "main",      true, 25.0),
            ex("cable_fly",               "Cable fly",               target, "secondary", true,  8.0),
            ex("lateral_raise_machine",   "Lateral raise machine",   target, "accessory", true,  5.0),
            ex("triceps_rope_pushdown",   "Triceps rope pushdown",   target, "finisher",  true, 15.0)
        );
        if ("pull".equals(target)) return Arrays.asList(
            ex("lat_pulldown",            "Lat pulldown",            target, "main",      true, 30.0),
            ex("seated_cable_row",        "Seated cable row",        target, "secondary", true, 25.0),
            ex("face_pull",               "Face pull",               target, "accessory", true, 10.0),
            ex("cable_curl",              "Cable curl",              target, "finisher",  true,  8.0)
        );
        return Arrays.asList(
            ex("leg_press",               "Leg press",               target, "main",      true, 40.0),
            ex("leg_curl",                "Leg curl",                target, "secondary", true, 20.0),
            ex("leg_extension",           "Leg extension",           target, "accessory", true, 20.0),
            ex("standing_calf_raise",     "Standing calf raise",     target, "finisher",  true, 20.0)
        );
    }

    private List<Exercise> dumbbellCandidates(String target) {
        if ("push".equals(target)) return Arrays.asList(
            ex("dumbbell_bench_press",             "Dumbbell bench press",             target, "main",      true, 10.0),
            ex("incline_dumbbell_press",           "Incline dumbbell press",           target, "secondary", true,  8.0),
            ex("lateral_raise",                    "Lateral raise",                    target, "accessory", true,  5.0),
            ex("overhead_triceps_extension",       "Overhead triceps extension",       target, "finisher",  true,  8.0)
        );
        if ("pull".equals(target)) return Arrays.asList(
            ex("dumbbell_row",                     "Dumbbell row",                     target, "main",      true, 12.0),
            ex("chest_supported_dumbbell_row",     "Chest-supported dumbbell row",     target, "secondary", true, 10.0),
            ex("rear_delt_raise",                  "Rear delt raise",                  target, "accessory", true,  5.0),
            ex("dumbbell_curl",                    "Dumbbell curl",                    target, "finisher",  true,  8.0)
        );
        return Arrays.asList(
            ex("goblet_squat",                     "Goblet squat",                     target, "main",      true, 12.0),
            ex("dumbbell_romanian_deadlift",       "Dumbbell Romanian deadlift",       target, "secondary", true, 10.0),
            ex("reverse_lunge",                    "Reverse lunge",                    target, "accessory", true,  0.0),
            ex("standing_calf_raise",              "Standing calf raise",              target, "finisher",  false, 0.0)
        );
    }

    private List<Exercise> bodyweightCandidates(String target) {
        if ("push".equals(target)) return Arrays.asList(
            ex("push_up",          "Push-up",          target, "main",      false, 0),
            ex("tempo_push_up",    "Tempo push-up",    target, "secondary", false, 0),
            ex("wall_slide",       "Wall slide",       target, "accessory", false, 0),
            ex("bench_dip",        "Bench dip",        target, "finisher",  false, 0)
        );
        if ("pull".equals(target)) return Arrays.asList(
            ex("towel_row",            "Towel row",            target, "main",      false, 0),
            ex("backpack_row",         "Backpack row",         target, "secondary", true,  5.0),
            ex("reverse_snow_angel",   "Reverse snow angel",   target, "accessory", false, 0),
            ex("isometric_curl",       "Isometric curl",       target, "finisher",  false, 0)
        );
        return Arrays.asList(
            ex("box_squat",        "Box squat",        target, "main",      false, 0),
            ex("glute_bridge",     "Glute bridge",     target, "secondary", false, 0),
            ex("step_up",          "Step-up",          target, "accessory", false, 0),
            ex("standing_calf_raise", "Standing calf raise", target, "finisher", false, 0)
        );
    }

    private Exercise ex(String id, String name, String target, String role,
                        boolean loadable, double defaultLoadKg) {
        return new Exercise(id, name, target, role, loadable, defaultLoadKg);
    }
}
