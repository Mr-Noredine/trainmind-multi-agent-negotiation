import java.util.List;
import java.util.ArrayList;

public class NegotiationDialogueFormatter {
    private NegotiationDialogueFormatter() {}

    // ═══════════════════════════════════════════════════════════════
    // Agent_P  (Performance) — propose, argumente, concède
    // ═══════════════════════════════════════════════════════════════

    public static String performanceProposal(TrainingProposal proposal, AthleteContext ctx) {
        if (proposal.round == 1) return round1Performance(proposal, ctx);
        return roundNPerformance(proposal, ctx);
    }

    private static String round1Performance(TrainingProposal proposal, AthleteContext ctx) {
        StringBuilder sb = new StringBuilder("[Agent_P]");
        sb.append(DialoguePerformatives.act(DialoguePerformatives.PROPOSE, "training_plan")).append(" ");
        String cat    = proposal.category;
        String target = proposal.target;
        double acwr   = getAcwr(ctx);
        double sleep  = ctx.getDouble("sleep_hours", 7.5);
        int fatigue   = ctx.getInt("fatigue", 4);
        String phase  = ctx.get("cycle_phase", "accumulation");
        String age    = ctx.get("training_age", "intermediate");
        boolean isRun = cat != null && cat.startsWith("running");
        boolean isRest = cat != null && (cat.contains("rest") || cat.contains("mobility"));

        sb.append(String.format(
            "Contexte : ACWR=%.2f, sommeil=%.1fh, fatigue=%d/10, phase=%s",
            acwr, sleep, fatigue, phase));
        if ("advanced".equals(age)) sb.append(", niveau avancé");
        else if ("novice".equals(age) || "debutant".equals(age)) sb.append(", niveau novice");
        sb.append(". ");

        // Recommandation avec justification
        if (isRest) {
            sb.append("Plan proposé : ");
            sb.append(RaisonApiService.friendlySessionType(cat));
            sb.append(". ");
            appendShortJustification(sb, cat, phase, acwr);
        } else {
            sb.append("Plan proposé : ");
            sb.append(RaisonApiService.friendlySessionType(cat));
            if (!isRun && target != null && !"recovery".equals(target)) {
                sb.append(String.format(" pour %s", friendlyTarget(target)));
            }
            sb.append(". ");
            appendShortJustification(sb, cat, phase, acwr);

            if (isRun && proposal.runningKm > 0) {
                sb.append(String.format("Distance : %.1f km. ", proposal.runningKm));
            } else {
                sb.append(String.format("RPE : %d/10. Volume : %d%%. ", proposal.rpe, proposal.volumePercent));
            }
        }

        if (!proposal.exercises.isEmpty() && !isRest) {
            List<String> top = topExercises(proposal.exercises, 2);
            if (!top.isEmpty()) {
                sb.append("Contenu : ").append(String.join(", ", top)).append(".");
            }
        }
        return sb.toString();
    }

    private static String roundNPerformance(TrainingProposal proposal, AthleteContext ctx) {
        StringBuilder sb = new StringBuilder("[Agent_P]");
        int round = proposal.round;
        boolean isRest = "complete_rest".equals(proposal.category);
        double acwr = getAcwr(ctx);
        int fatigue = ctx.getInt("fatigue", 4);
        double sleep = ctx.getDouble("sleep_hours", 7.5);

        sb.append(performancePerformative(round)).append(" ");

        // Ouverture selon round et contexte
        if (isRest) {
            sb.append("Accord. Les données imposent le repos complet.");
            return sb.toString();
        }

        String[] openings = openingsForRound(round, acwr, fatigue, sleep, proposal);
        sb.append(openings[0]).append(" ");

        // Concessions intégrées
        List<String> integrated = getIntegratedConstraints(proposal);
        if (!integrated.isEmpty()) {
            sb.append("Intégré : ").append(String.join(", ", integrated)).append(". ");
        }

        sb.append(String.format("Nouveau plan : %s",
            RaisonApiService.friendlySessionType(proposal.category)));
        if (proposal.target != null && !"recovery".equals(proposal.target)
                && !proposal.category.startsWith("running")) {
            sb.append(String.format(" pour %s", friendlyTarget(proposal.target)));
        }
        sb.append(String.format(", RPE %d, volume %d%%", proposal.rpe, proposal.volumePercent));
        if (proposal.category != null && proposal.category.startsWith("running")
                && proposal.runningKm > 0) {
            sb.append(String.format(", distance %.1f km", proposal.runningKm));
        }
        sb.append(".");

        if (round >= 4 && !integrated.isEmpty()) {
            sb.append(" " + openings[1]);
        }
        return sb.toString();
    }

    private static String[] openingsForRound(int round, double acwr, int fatigue, double sleep,
                                              TrainingProposal proposal) {
        // [0] = ouverture principale, [1] = argument de défense additionnel
        if (round == 2) {
            if (fatigue >= 7)
                return new String[]{"Fatigue prise en compte.",
                    "Adaptation faite pour réduire le risque."};
            if (sleep < 6.5)
                return new String[]{"Sommeil bas pris en compte.",
                    "Séance réduite plutôt que séance risquée."};
            if (acwr > 1.3)
                return new String[]{"ACWR élevé : j'allège.",
                    "Charge contrôlée."};
            return new String[]{"J'ajuste.",
                "Les garanties sont intégrées."};
        }
        if (round == 3) {
            if (proposal.volumePercent < 100)
                return new String[]{"Version révisée.",
                    "Volume réduit, séance encore utile."};
            return new String[]{"Version vérifiée.",
                "Contraintes intégrées."};
        }
        if (round == 4) {
            return new String[]{"Dernier ajustement.",
                "Si le risque reste haut, on bascule repos."};
        }
        return new String[]{"Version finale proposée.",
            "Toutes les garanties demandées sont présentes."};
    }

    // ═══════════════════════════════════════════════════════════════
    // Agent_R  (Recovery) — analyse, objecte, valide
    // ═══════════════════════════════════════════════════════════════

    public static String recoveryAnswer(NegotiationFeedback feedback, AthleteContext ctx) {
        StringBuilder sb = new StringBuilder("[Agent_R]");

        if (feedback.accepted) {
            sb.append(DialoguePerformatives.act(DialoguePerformatives.ACCEPT_PROPOSAL, "safe_plan")).append(" ");
            return acceptanceMessage(sb, feedback, ctx);
        }
        sb.append(recoveryPerformative(feedback.round)).append(" ");

        double sleep  = ctx.getDouble("sleep_hours", 7.5);
        int fatigue   = ctx.getInt("fatigue", 4);
        int jointPain = ctx.getInt("joint_pain", 0);
        int soreness  = ctx.getInt("soreness", 3);
        double acwr   = getAcwr(ctx);
        String hrv    = ctx.get("hrv", "normal");
        String pzone  = ctx.get("joint_pain_zone", "none");
        String szone  = ctx.get("soreness_zone", "none");
        int round     = feedback.round;

        sb.append(riskOpening(feedback.riskScore, feedback.riskLevel, round)).append(" ");

        List<String> alarms = buildAlarms(sleep, fatigue, jointPain, soreness, acwr, hrv, pzone, szone);
        if (!alarms.isEmpty()) {
            if (alarms.size() == 1) {
                sb.append("Alerte : ").append(alarms.get(0)).append(". ");
            } else {
                sb.append("Alertes : ");
                for (int i = 0; i < Math.min(alarms.size(), 3); i++) {
                    if (i > 0) sb.append(" | ");
                    sb.append(alarms.get(i));
                }
                sb.append(". ");
            }
        }

        List<String> demands = buildDemands(feedback.constraints, ctx, feedback.riskScore);
        if (!demands.isEmpty()) {
            if (round >= 3 && feedback.riskScore >= 30
                    && !feedback.constraints.contains("complete_rest_required")) {
                sb.append("Je maintiens : ");
                sb.append(String.join(", ", demands)).append(".");
                if (round >= 4) {
                    sb.append(" Si c'est intégré, j'accepte sous contrôle.");
                }
                return sb.toString();
            }
            sb.append(demandsIntro(round, feedback.riskScore)).append(" ");
            sb.append(String.join(", ", demands)).append(".");
        } else {
            sb.append("Aucune nouvelle contrainte.");
        }

        if (round >= 3 && feedback.riskScore < 40) {
            sb.append(" Presque OK.");
        } else if (round >= 4) {
            sb.append(" À ajuster.");
        }

        return sb.toString();
    }

    private static String acceptanceMessage(StringBuilder sb, NegotiationFeedback feedback, AthleteContext ctx) {
        int score = feedback.riskScore;
        double sleep = ctx.getDouble("sleep_hours", 7.5);
        int fatigue = ctx.getInt("fatigue", 4);
        String hrv = ctx.get("hrv", "normal");

        if (score == 0) {
            if ("high".equals(hrv) && sleep >= 8.0 && fatigue <= 3) {
                sb.append("Accord total. Conditions optimales.");
            } else {
                sb.append("Accord total. Programme validé.");
            }
        } else if (score < 15) {
            sb.append(String.format(
                "Accord. Risque faible (%d/100). Garanties OK.",
                score));
        } else if (score < 30) {
            sb.append(String.format(
                "J'accepte. Risque modéré (%d/100), contrôlé par les contraintes.",
                score));
        } else {
            sb.append(String.format(
                "Accord contraint. Risque %d/100, protections obligatoires.",
                score));
        }
        return sb.toString();
    }

    private static String riskOpening(int score, String level, int round) {
        if ("critical".equals(level)) {
            String[] opts = {
                String.format("Refus. Risque critique : %d/100.", score),
                String.format("Non validable : %d/100 de risque.", score)
            };
            return opts[round % opts.length];
        }
        if ("high".equals(level)) {
            String[] opts = {
                String.format("Risque haut : %d/100.", score),
                String.format("Pas encore validable : %d/100.", score)
            };
            return opts[round % opts.length];
        }
        if ("moderate".equals(level)) {
            String[] opts = {
                String.format("Risque modéré : %d/100.", score),
                String.format("Encore incomplet : %d/100.", score),
                String.format("Presque validable : %d/100.", score)
            };
            return opts[round % opts.length];
        }
        String[] opts = {
            "Risque faible.",
            "Chiffres OK.",
            "Sécurité presque complète."
        };
        return opts[round % opts.length];
    }

    private static String performancePerformative(int round) {
        if (round == 2) return DialoguePerformatives.act(DialoguePerformatives.PROPOSE, "safer_training_plan");
        if (round == 3) return DialoguePerformatives.act(DialoguePerformatives.PROPOSE, "revised_training_plan");
        if (round == 4) return DialoguePerformatives.act(DialoguePerformatives.PROPOSE, "plan_with_guarantees");
        return DialoguePerformatives.act(DialoguePerformatives.PROPOSE, "final_training_plan");
    }

    private static String recoveryPerformative(int round) {
        if (round == 1) return DialoguePerformatives.act(DialoguePerformatives.REJECT_PROPOSAL, "unsafe_or_incomplete_plan");
        if (round == 2) return DialoguePerformatives.act(DialoguePerformatives.PROPOSE, "safety_counter_constraints");
        if (round == 3) return DialoguePerformatives.act(DialoguePerformatives.QUERY_IF, "safety_guarantee");
        if (round == 4) return DialoguePerformatives.act(DialoguePerformatives.QUERY_IF, "stop_rule_defined");
        return DialoguePerformatives.act(DialoguePerformatives.QUERY_IF, "missing_safety_argument");
    }

    private static List<String> buildAlarms(double sleep, int fatigue, int joint, int soreness,
                                             double acwr, String hrv, String pzone, String szone) {
        List<String> a = new ArrayList<>();

        if (joint >= 7)
            a.add(0, String.format("douleur articulaire %d/10 (%s)", joint, pzone));
        else if (joint >= 4)
            a.add(String.format("douleur %d/10 (%s)", joint, pzone));

        if (acwr > 1.5)
            a.add(0, String.format("ACWR=%.2f, zone rouge", acwr));
        else if (acwr > 1.3)
            a.add(String.format("ACWR=%.2f, zone orange", acwr));

        if (sleep < 5.5)
            a.add(String.format("sommeil=%.1fh, très bas", sleep));
        else if (sleep < 6.5)
            a.add(String.format("sommeil=%.1fh, insuffisant", sleep));

        if (fatigue >= 9)
            a.add(String.format("fatigue=%d/10, très haute", fatigue));
        else if (fatigue >= 7)
            a.add(String.format("fatigue=%d/10", fatigue));

        if (soreness >= 8)
            a.add(String.format("courbatures=%d/10 (%s)", soreness, szone));
        else if (soreness >= 6 && !"none".equals(szone))
            a.add(String.format("courbatures=%d/10 (%s)", soreness, szone));

        if ("low".equals(hrv))
            a.add("HRV bas");

        return a;
    }

    private static String demandsIntro(int round, int riskScore) {
        if (riskScore >= 40)   return "Obligatoire :";
        if (round == 1)        return "Demande :";
        if (round == 2)        return "À ajouter :";
        if (round >= 3)        return "Dernier point :";
        return "Demande :";
    }

    private static List<String> buildDemands(List<String> constraints, AthleteContext ctx, int risk) {
        List<String> demands = new ArrayList<>();
        if (constraints.contains("complete_rest_required"))
            demands.add("repos complet");
        if (constraints.contains("rest_times_defined")) {
            if (!constraints.contains("complete_rest_required"))
                demands.add("repos entre séries");
        }
        if (constraints.contains("stop_rule_defined"))
            demands.add("règle d'arrêt");
        if (constraints.contains("load_validated"))
            demands.add("charges validées");
        if (constraints.contains("distance_validated"))
            demands.add("distance validée");
        if (constraints.contains("reduce_volume"))
            demands.add("volume -20%");
        if (constraints.contains("low_intensity_only"))
            demands.add("intensité légère");
        if (constraints.contains("limit_rpe"))
            demands.add("RPE plafonné");
        if (constraints.contains("avoid_pain_zone"))
            demands.add("zone douloureuse évitée");
        if (constraints.contains("plan_validated"))
            demands.add("plan validé");
        if (constraints.contains("max_rpe_6"))
            demands.add("RPE max 6");
        else if (constraints.contains("max_rpe_7"))
            demands.add("RPE max 7");
        for (String c : constraints) {
            if (c.startsWith("avoid_zone_")) {
                String zone = c.replace("avoid_zone_", "").replace("_", " ");
                demands.add("éviter zone " + zone);
            }
        }
        return demands;
    }

    private static void appendShortJustification(StringBuilder sb, String cat, String phase, double acwr) {
        String just = shortPhaseJustification(cat, phase, acwr);
        if (!just.isEmpty()) {
            sb.append("Argument : ").append(just).append(". ");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Message de conclusion
    // ═══════════════════════════════════════════════════════════════

    public static String finalAgreement(TrainingProposal proposal, int rounds) {
        String cat = RaisonApiService.friendlySessionType(proposal.category);
        boolean isRun  = proposal.category != null && proposal.category.startsWith("running");
        boolean isRest = "complete_rest".equals(proposal.category);

        String details;
        if (isRest) {
            details = "repos complet — priorité récupération";
        } else if (isRun && proposal.runningKm > 0) {
            details = String.format("%s — %.1f km, RPE %d", cat, proposal.runningKm, proposal.rpe);
        } else {
            details = String.format("%s pour %s — RPE %d, volume %d%%",
                cat, friendlyTarget(proposal.target), proposal.rpe, proposal.volumePercent);
        }

        String roundsStr;
        if (rounds == 1) {
            roundsStr = "Accord immédiat — les deux agents étaient alignés dès le départ.";
        } else if (rounds == 2) {
            roundsStr = "Accord en 2 échanges — quelques ajustements mineurs.";
        } else if (rounds <= 4) {
            roundsStr = String.format("Accord trouvé en %d échanges — négociation productive.", rounds);
        } else {
            roundsStr = String.format("Accord après %d échanges — garanties de sécurité formalisées.", rounds);
        }

        return String.format("[Agent_P] %s Programme transmis au manager : %s.", roundsStr, details);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private static String shortPhaseJustification(String cat, String phase, double acwr) {
        if (cat == null) return "";
        if (cat.contains("deload"))   return "décharge prioritaire";
        if (cat.contains("rest"))     return "récupération prioritaire";
        if (cat.contains("strength") && "intensification".equals(phase))
            return "force adaptée à l'intensification";
        if (cat.contains("strength") && "peaking".equals(phase))
            return "force lourde, volume contrôlé";
        if (cat.contains("power")    && "peaking".equals(phase))
            return "pic de forme, travail explosif";
        if (cat.contains("hypertrophy") && "accumulation".equals(phase))
            return "accumulation, volume utile";
        if (cat.contains("hypertrophy") && "intensification".equals(phase))
            return "volume réduit, intensité montée";
        if (cat.contains("interval") && "intensification".equals(phase))
            return "intensification, VO2max";
        if (cat.contains("easy") && acwr > 1.3)
            return String.format("ACWR=%.2f, charge contrôlée", acwr);
        if (cat.contains("long") && "accumulation".equals(phase))
            return "accumulation, endurance fondamentale";
        if (cat.contains("tempo") && "intensification".equals(phase))
            return "intensification, seuil lactique";
        if (cat.contains("interval") && "peaking".equals(phase))
            return "pic de forme, fractionné court";
        return "";
    }

    private static List<String> getIntegratedConstraints(TrainingProposal proposal) {
        List<String> pts = new ArrayList<>();
        if (proposal.constraints.contains("rest_times_defined")) {
            String rt = restTimeLabel(proposal.category);
            pts.add("repos " + rt);
        }
        if (proposal.constraints.contains("stop_rule_defined"))
            pts.add("règle d'arrêt");
        if (proposal.constraints.contains("load_validated"))
            pts.add("charges validées");
        if (proposal.constraints.contains("distance_validated"))
            pts.add("distance validée");
        if (proposal.constraints.contains("reduce_volume"))
            pts.add("volume " + proposal.volumePercent + "%");
        if (proposal.constraints.contains("limit_rpe")
                || proposal.constraints.contains("max_rpe_6")
                || proposal.constraints.contains("max_rpe_7"))
            pts.add("RPE max " + proposal.rpe);
        if (proposal.constraints.contains("low_intensity_only"))
            pts.add("intensité légère");
        if (proposal.constraints.contains("avoid_pain_zone"))
            pts.add("zone douloureuse évitée");
        if (proposal.constraints.contains("plan_validated"))
            pts.add("plan validé");
        return pts;
    }

    private static String restTimeLabel(String cat) {
        if (cat == null) return "2 min";
        if (cat.contains("strength") || cat.contains("power")) return "3-5 min";
        if (cat.contains("hypertrophy")) return "90s-2 min";
        return "60-90s";
    }

    private static List<String> topExercises(List<String> exercises, int n) {
        List<String> result = new ArrayList<>();
        for (String ex : exercises) {
            if (result.size() >= n) break;
            if (!ex.startsWith("→")) result.add(shortExercise(ex));
        }
        return result;
    }

    private static String shortExercise(String line) {
        int dash = line.indexOf("—");
        return dash > 0 ? line.substring(0, dash).trim() : line;
    }

    private static double getAcwr(AthleteContext ctx) {
        double stored = ctx.getDouble("acwr", 0);
        if (stored > 0) return stored;
        double weekly = ctx.getDouble("weekly_km", 0);
        double chronic = ctx.getDouble("chronic_km", 0);
        if (chronic > 0 && weekly > 0) return weekly / chronic;
        return 1.0;
    }

    public static String friendlyTarget(String target) {
        if (target == null) return "la séance";
        switch (target) {
            case "pull":     return "dos/biceps";
            case "push":     return "pecto/épaules/triceps";
            case "legs":     return "jambes";
            case "running":  return "la course";
            case "recovery": return "la récupération";
            default:         return target;
        }
    }

    private static String friendlyRisk(String level) {
        if ("critical".equals(level)) return "critique";
        if ("high".equals(level))     return "élevé";
        if ("moderate".equals(level)) return "modéré";
        return "faible";
    }
}
