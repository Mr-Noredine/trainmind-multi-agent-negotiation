import java.util.List;

public class TrainingPlanFormatter {
    private TrainingPlanFormatter() {}

    private static final String SEP  = "─────────────────────────────────────────────────────";
    private static final String SEP2 = "═════════════════════════════════════════════════════";

    public static String format(TrainingProposal proposal, int rounds) {
        StringBuilder out = new StringBuilder();

        out.append("\n").append(SEP2).append("\n");
        out.append("  VOTRE PROGRAMME DU JOUR\n");
        out.append(SEP2).append("\n\n");

        // Session type
        String sessionLabel = friendlyCategory(proposal.category);
        out.append("  Seance   : ").append(sessionLabel).append("\n");

        // Target
        String targetLabel = friendlyTarget(proposal.target);
        if (targetLabel != null) {
            out.append("  Groupe   : ").append(targetLabel).append("\n");
        }

        // Effort level — natural language, no RPE/volume codes
        out.append("  Intensite: ").append(friendlyIntensity(proposal.rpe)).append("\n");

        // Running distance if applicable
        if (proposal.category != null && proposal.category.startsWith("running") && proposal.runningKm > 0) {
            out.append("  Distance : ").append(String.format("%.1f km", proposal.runningKm)).append("\n");
        }

        out.append("\n").append(SEP).append("\n");
        out.append("  DEROULEMENT DE LA SEANCE\n");
        out.append(SEP).append("\n\n");

        // Exercises — clean up internal annotations before display
        List<String> exercises = proposal.exercises;
        if (exercises.isEmpty()) {
            out.append("  Aucun exercice specifie.\n");
        } else {
            for (String line : exercises) {
                out.append("  ").append(cleanExerciseLine(line)).append("\n");
            }
        }

        // Safety rules — natural language
        out.append("\n").append(SEP).append("\n");
        out.append("  CONSIGNES DE SECURITE\n");
        out.append(SEP).append("\n\n");

        out.append(safetyBlock(proposal));

        // Negotiation summary
        out.append("\n").append(SEP).append("\n");
        String roundsLabel = rounds == 1
                ? "accord immediat (1 echange)"
                : "accord apres " + rounds + " echanges entre les agents";
        out.append("  Validation : ").append(roundsLabel).append("\n");
        out.append(SEP2).append("\n");

        return out.toString();
    }

    // ═══ Helpers ════════════════════════════════════════════════════════════

    private static String friendlyCategory(String cat) {
        if (cat == null) return "Seance non definie";
        // Strength/Hypertrophy/Power/Technique by target (e.g. legs_hypertrophy, push_strength)
        if (cat.endsWith("_strength"))    return "Musculation — Force maximale";
        if (cat.endsWith("_hypertrophy")) return "Musculation — Developpement musculaire";
        if (cat.endsWith("_power"))       return "Musculation — Puissance explosive";
        if (cat.endsWith("_technique"))   return "Musculation — Technique et apprentissage";
        switch (cat) {
            case "strength_session":     return "Musculation — Force maximale";
            case "hypertrophy_session":  return "Musculation — Developpement musculaire";
            case "power_session":        return "Musculation — Puissance explosive";
            case "technique_session":    return "Musculation — Technique et apprentissage";
            case "running_easy":         return "Course a pied — Footing leger (endurance de base)";
            case "running_tempo":        return "Course a pied — Allure soutenue (seuil lactique)";
            case "running_long":         return "Course a pied — Sortie longue (endurance fondamentale)";
            case "running_intervals":    return "Course a pied — Fractionne (vitesse et VO2max)";
            case "mobility_deload":      return "Mobilite et recuperation active";
            case "complete_rest":        return "Repos complet";
            case "repos_complet":        return "Repos complet";
            case "sommeil_prioritaire":  return "Priorite sommeil et recuperation profonde";
            case "cardio_liss":          return "Cardio doux (marche rapide ou velo leger)";
            case "recuperation_active":  return "Recuperation active (mouvements legers)";
            case "mobilite_etirements":  return "Mobilite et etirements";
            case "bain_contraste":       return "Bain contraste (chaud/froid)";
            default:                     return capitalize(cat.replace("_", " "));
        }
    }

    private static String friendlyTarget(String target) {
        if (target == null || "recovery".equals(target)) return null;
        switch (target) {
            case "pull":    return "Dos et biceps";
            case "push":    return "Pectoraux, epaules et triceps";
            case "legs":    return "Jambes (quadriceps, ischio-jambiers, mollets)";
            case "running": return null;
            default:        return capitalize(target.replace("_", " "));
        }
    }

    private static String friendlyIntensity(int rpe) {
        if (rpe <= 4)  return "Tres facile — effort de recuperation";
        if (rpe == 5)  return "Facile — confort total, on peut parler normalement";
        if (rpe == 6)  return "Moderee — legere acceleration du souffle";
        if (rpe == 7)  return "Moderee a elevee — on peut encore parler, mais pas longtemps";
        if (rpe == 8)  return "Elevee — effort soutenu, la conversation devient difficile";
        if (rpe == 9)  return "Tres elevee — quasi maximal, quelques mots seulement";
        return "Maximale — effort total, court et intense";
    }

    private static String safetyBlock(TrainingProposal proposal) {
        StringBuilder sb = new StringBuilder();
        List<String> constraints = proposal.constraints;
        boolean isRunning = proposal.category != null && proposal.category.startsWith("running");
        boolean isRest = "complete_rest".equals(proposal.category) || "repos_complet".equals(proposal.category);

        if (isRest) {
            sb.append("  - Journee de repos complet. Aucun effort physique intense.\n");
            sb.append("  - Marche legere, etirements tres doux autorises.\n");
            sb.append("  - Priorisez un sommeil de qualite et une alimentation recuperatrice.\n");
            return sb.toString();
        }

        // Rest times
        if (constraints.contains("rest_times_defined")) {
            if (isRunning) {
                sb.append("  - Recuperation entre les repetitions : completement indiquee dans la seance.\n");
                sb.append("  - Retour au calme de 5 minutes obligatoire en fin de seance.\n");
            } else if ("recovery".equals(proposal.target)) {
                sb.append("  - Gardez l'intensite tres facile : respiration normale tout au long.\n");
                sb.append("  - Aucune douleur ne doit apparaitre pendant les mouvements.\n");
            } else {
                String restTime = restTimeByCategory(proposal.category);
                sb.append("  - Temps de repos entre les series : ").append(restTime).append(".\n");
            }
        }

        // Stop rule
        if (constraints.contains("stop_rule_defined")) {
            if (isRunning) {
                sb.append("  - Stopper l'effort si : douleur, technique qui se degrade, ou souffle completement coupe.\n");
            } else {
                sb.append("  - Stopper ou allegez si : douleur articulaire, crampe ou fatigue soudaine.\n");
            }
        }

        // Volume reduction
        if (constraints.contains("reduce_volume")) {
            sb.append("  - Volume reduit aujourd'hui : ne cherchez pas a aller au maximum.\n");
        }

        // Low intensity constraint
        if (constraints.contains("low_intensity_only")) {
            sb.append("  - Intensite legere uniquement — pas de record, pas de performance aujourd'hui.\n");
        }

        // RPE cap
        if (constraints.contains("max_rpe_6")) {
            sb.append("  - Plafond d'effort : modere (6/10). Des que ca devient difficile, ralentissez.\n");
        } else if (constraints.contains("max_rpe_7")) {
            sb.append("  - Plafond d'effort : modere-eleve (7/10). Stoppez avant la limite.\n");
        }
        if (constraints.contains("limit_rpe")) {
            sb.append("  - Plafond d'effort adapte au risque du jour : restez sous l'intensite proposee.\n");
        }

        // Avoid painful zone
        if (constraints.contains("avoid_pain_zone")) {
            sb.append("  - Evitez toute zone douloureuse pendant la seance.\n");
        }
        for (String c : constraints) {
            if (c.startsWith("avoid_zone_")) {
                String zone = c.replace("avoid_zone_", "").replace("_", " ");
                sb.append("  - Evitez tout exercice sollicitant la zone douloureuse : ").append(zone).append(".\n");
            }
        }

        // Validated load
        if (constraints.contains("load_validated")) {
            sb.append("  - Charges verifiees sur votre historique — restez dans les valeurs proposees.\n");
        }
        if (constraints.contains("distance_validated")) {
            sb.append("  - Distance calculee en fonction de votre kilometrage habituel — ne la depassez pas.\n");
        }
        if (constraints.contains("duration_validated")) {
            sb.append("  - Duree limitee et validee : gardez la seance courte et facile.\n");
        }
        if (constraints.contains("plan_validated")) {
            sb.append("  - Plan valide par le moteur Recovery : appliquer toutes les consignes ci-dessus.\n");
        }

        // Default if no specific constraints
        if (sb.length() == 0) {
            sb.append("  - Ecoutez votre corps : si quelque chose fait mal, arretez.\n");
            sb.append("  - Hydratez-vous bien avant, pendant et apres.\n");
        }

        return sb.toString();
    }

    private static String restTimeByCategory(String cat) {
        if (cat == null) return "2 minutes";
        if (cat.contains("strength") || cat.contains("power")) return "3 a 5 minutes (effort nerveux : recuperation complete)";
        if (cat.contains("hypertrophy")) return "90 secondes a 2 minutes";
        if (cat.contains("technique")) return "2 minutes (concentrez-vous sur la qualite du mouvement)";
        return "60 a 90 secondes";
    }

    private static String cleanExerciseLine(String line) {
        if (line == null) return "";
        // Conserver la note "poids de depart" mais rendre lisible
        boolean isDefault = line.contains("← poids de depart suggere");
        String cleaned = line.replace("  ← poids de depart suggere", "");
        // Remove (RPE X) annotations — internal expert notation
        cleaned = cleaned.replaceAll("\\s*\\(RPE\\s*\\d+\\)", "");
        // Replace " @ X kg" with " avec X kg" for natural French
        cleaned = cleaned.replaceAll("\\s*@\\s*(\\S+\\s*kg)", " avec $1");
        // Replace " @ X km/h" with running pace description
        cleaned = cleaned.replaceAll("\\s*@\\s*([\\d,\\.]+\\s*km/h)", " a $1");
        if (isDefault) cleaned += "  (poids de depart — a ajuster selon votre niveau)";
        return cleaned;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
