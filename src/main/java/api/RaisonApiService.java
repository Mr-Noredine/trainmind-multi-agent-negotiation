import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Adaptateur HTTP vers la plateforme rAison.
 * Les règles métier restent dans les projets rAison ; cette classe assemble les
 * faits utilisateur, applique les contraintes retournées et construit le plan lisible.
 */
public class RaisonApiService implements RaisonService {

    private static final String DEFAULT_BASE_URL = "https://api.ai-raison.com";
    private static final List<String> RECOVERY_DECISION_OPTIONS = Arrays.asList(
            "repos_complet", "sommeil_prioritaire", "cardio_liss",
            "recuperation_active", "mobilite_etirements", "bain_contraste");
    private static final List<String> RECOVERY_ASSESSMENT_OPTIONS = Arrays.asList(
            "risk_low", "risk_moderate", "risk_high", "risk_critical",
            "complete_rest_required", "low_intensity_only", "reduce_volume",
            "limit_rpe", "avoid_pain_zone", "distance_validated",
            "stop_rule_defined", "plan_validated");
    private static final List<String> PERFORMANCE_SESSION_OPTIONS = Arrays.asList(
            "strength_session","hypertrophy_session","explosive_power_session",
            "technique_session","running_easy","running_tempo",
            "running_long","running_intervals","mobility_session","deload_session");
    private static final List<String> PERFORMANCE_TARGET_OPTIONS = Arrays.asList(
            "target_running", "target_recovery", "target_push", "target_pull", "target_legs");
    private static final List<String> PERFORMANCE_RPE_OPTIONS = Arrays.asList(
            "rpe_rest", "rpe_easy", "rpe_moderate", "rpe_hard", "rpe_very_hard");
    private static final List<String> PERFORMANCE_VOLUME_OPTIONS = Arrays.asList(
            "volume_none", "volume_low", "volume_reduced", "volume_normal");
    private static final List<String> PERFORMANCE_DISTANCE_OPTIONS = Arrays.asList(
            "distance_none", "distance_short", "distance_medium", "distance_long");
    private static final String RAISON_QUERY_PATH = "/executions/%s/latest";

    private final TrainMindConfig config;
    private final ExerciseLibrary exerciseLibrary = new ExerciseLibrary();
    private final Map<String, ConstraintApplier> constraintAppliers = new HashMap<>();

    public RaisonApiService(TrainMindConfig config) {
        this.config = config;
        registerConstraintAppliers();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Interface RaisonService
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public TrainingProposal initialPerformanceProposal(AthleteContext ctx) {
        TrainingProposal proposal = new TrainingProposal();
        proposal.round = 1;
        proposal.phase = "level_1_session_choice";
        String apiSession = fetchPerformanceDecision(ctx, false);
        String apiTarget = fetchPerformanceTarget(ctx, false);
        proposal.target = mapTarget(apiTarget);
        proposal.category = mapSessionToCategory(apiSession, proposal.target, ctx);
        applyPerformanceParameters(ctx, proposal, false);

        // Arguments contextualisés pour la négociation
        double acwr  = ctx.getDouble("acwr", 1.0);
        double sleep = ctx.getDouble("sleep_hours", 7.5);
        int fatigue  = ctx.getInt("fatigue", 4);
        String phase = ctx.get("cycle_phase", "accumulation");

        proposal.arguments.add(String.format(
            "[rAison/%s] ACWR=%.2f | sommeil=%.1fh | fatigue=%d/10 | phase=%s → %s",
            config.performanceProjectId, acwr, sleep, fatigue, phase,
            apiSession
        ));
        proposal.arguments.add(String.format(
            "Performance: %s recommandé pour %s (RPE cible %d, volume %d%%)",
            friendlySessionType(proposal.category), proposal.target,
            proposal.rpe, proposal.volumePercent
        ));

        fillExercises(ctx, proposal);
        return proposal;
    }

    @Override
    public TrainingProposal refinePerformanceProposal(
            AthleteContext ctx, TrainingProposal previous,
            NegotiationFeedback feedback, int nextRound) {

        TrainingProposal next = previous.copy();
        next.round = nextRound;

        // Snapshot avant application : permet de détecter les nouveaux contraintes
        Set<String> priorConstraints = new HashSet<>(next.constraints);

        for (String constraint : feedback.constraints) {
            applyConstraint(ctx, next, constraint);
        }

        if (feedback.riskScore >= 30 && !"complete_rest".equals(next.category)) {
            String newSession = fetchPerformanceDecision(ctx, true);
            String newTarget = fetchPerformanceTarget(ctx, true);
            String newCat = mapSessionToCategory(newSession, mapTarget(newTarget), ctx);
            if (!newCat.equals(next.category)) {
                next.target = mapTarget(newTarget);
                next.category = newCat;
                next.arguments.add(String.format(
                    "Performance: risque=%d → réévaluation rAison → %s",
                    feedback.riskScore, friendlySessionType(next.category)
                ));
            }
            applyPerformanceParameters(ctx, next, true);
        }

        // Générer les messages explicatifs pour les contraintes nouvellement ajoutées
        // (qu'elles viennent directement de feedback ou via plan_validated → addPlanValidationConstraints)
        if (next.constraints.contains("rest_times_defined") && !priorConstraints.contains("rest_times_defined")) {
            next.arguments.add(String.format(
                "Performance: repos garantis — %s entre séries principales, %s sur accessoires",
                restTimeFor(next.category), restTimeForAccessory(next.category)
            ));
        }
        if (next.constraints.contains("stop_rule_defined") && !priorConstraints.contains("stop_rule_defined")) {
            next.arguments.add("Performance: règle d'arrêt — stopper si douleur articulaire ou RPE réel >"
                + (next.rpe + 1));
        }
        if (next.constraints.contains("load_validated") && !priorConstraints.contains("load_validated")) {
            next.arguments.add("Performance: charges validées sur historique récent (progression conservatrice)");
        }
        if (next.constraints.contains("distance_validated") && !priorConstraints.contains("distance_validated")) {
            next.arguments.add(String.format(
                "Performance: distance %.1fkm calculée (ACWR cible=1.0, chronic=%.0fkm/sem)",
                next.runningKm, ctx.getDouble("chronic_km", 25)
            ));
        }

        next.phase = "level_2_session_details";
        next.exercises.clear();
        fillExercises(ctx, next);
        return next;
    }

    @Override
    public NegotiationFeedback reviewRecoveryProposal(
            AthleteContext ctx, TrainingProposal proposal, int round) {

        NegotiationFeedback feedback = new NegotiationFeedback();
        feedback.round = round;
        String apiRec = fetchRecoveryDecision(ctx);
        applyRecoveryAssessment(ctx, proposal, feedback, apiRec);

        // Arguments contextualisés Recovery
        double acwr   = ctx.getDouble("acwr", 1.0);
        double sleep  = ctx.getDouble("sleep_hours", 7.5);
        int fatigue   = ctx.getInt("fatigue", 4);
        int jointPain = ctx.getInt("joint_pain", 0);
        int soreness  = ctx.getInt("soreness", 3);
        String hrv    = ctx.get("hrv", "normal");

        feedback.arguments.add(String.format(
            "[rAison/%s] sommeil=%.1fh | fatigue=%d/10 | douleur=%d/10 | courbatures=%d/10 | ACWR=%.2f | HRV=%s → %s",
            config.recoveryProjectId, sleep, fatigue, jointPain, soreness, acwr, hrv,
            apiRec != null ? apiRec : "aucune alarme"
        ));

        // Construire les demandes spécifiques
        List<String> demands = new ArrayList<>();
        if (jointPain >= 4)
            demands.add(String.format("douleur articulaire %d/10 → règle d'arrêt obligatoire", jointPain));
        if (acwr > 1.3)
            demands.add(String.format("ACWR=%.2f (zone orange, max recommandé=1.3) → volume réduit", acwr));
        if (sleep < 6.5)
            demands.add(String.format("sommeil=%.1fh insuffisant → RPE maximum abaissé", sleep));
        if (soreness >= 7)
            demands.add(String.format("courbatures %d/10 → éviter les mêmes groupes musculaires", soreness));
        if ("low".equals(hrv))
            demands.add("HRV bas → système nerveux sous-récupéré → réduire l'intensité");

        if (!demands.isEmpty()) {
            feedback.arguments.add("Recovery: " + String.join(" | ", demands));
        } else {
            feedback.arguments.add(String.format(
                "Recovery: paramètres normaux (risque=%d/100=%s)",
                feedback.riskScore, feedback.riskLevel
            ));
        }

        boolean completeRestReady = "complete_rest".equals(proposal.category);
        boolean brainAccepted = completeRestReady || feedback.constraints.contains("plan_validated");
        feedback.accepted = ArgumentationProtocolFSM.acceptIfProtocolComplete(
                ctx, proposal, feedback, brainAccepted);
        feedback.arguments.add(feedback.accepted
            ? String.format("Recovery: programme validé. Risque=%d/100 (%s).", feedback.riskScore, feedback.riskLevel)
            : brainAccepted
                ? "Recovery: accord de principe, mais le protocole argumentatif exige encore des garanties explicites."
            : "Recovery: ajustements requis.");
        return feedback;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Appels HTTP → plateforme rAison
    // ═══════════════════════════════════════════════════════════════════════

    private String fetchPerformanceDecision(AthleteContext ctx, boolean withRisk) {
        List<RaisonElementInput> elements = buildPerformanceElements(ctx, withRisk);
        if (elements.isEmpty()) return null;
        return requireBrainOption("Performance/session",
                callApi(config.performanceProjectId, elements, PERFORMANCE_SESSION_OPTIONS));
    }

    private String fetchPerformanceTarget(AthleteContext ctx, boolean withRisk) {
        List<RaisonElementInput> elements = buildPerformanceElements(ctx, withRisk);
        return requireBrainOption("Performance/target",
                callApi(config.performanceProjectId, elements, PERFORMANCE_TARGET_OPTIONS));
    }

    private String fetchPerformanceParameter(AthleteContext ctx, TrainingProposal proposal,
                                             boolean withRisk, List<String> options, String label) {
        List<RaisonElementInput> elements = buildPerformanceElements(ctx, withRisk);
        elements.add(RaisonElementInput.atom(sessionOptionFromCategory(proposal.category)));
        elements.add(RaisonElementInput.atom("target_" + proposal.target));
        return requireBrainOption(label, callApi(config.performanceProjectId, elements, options));
    }

    private String requireBrainOption(String label, String option) {
        if (option == null || option.isEmpty()) {
            throw new IllegalStateException(label + ": aucune réponse rAison. Décision Java interdite.");
        }
        return option;
    }

    private String fetchRecoveryDecision(AthleteContext ctx) {
        List<RaisonElementInput> elements = buildRecoveryElements(ctx);
        if (elements.isEmpty()) return null;
        return callApi(config.recoveryProjectId, elements, RECOVERY_DECISION_OPTIONS);
    }

    private List<String> fetchRecoveryAssessment(AthleteContext ctx, TrainingProposal proposal, String recoveryDecision) {
        List<RaisonElementInput> elements = buildRecoveryAssessmentElements(ctx, proposal, recoveryDecision);
        return callApiSolutions(config.recoveryProjectId, elements, RECOVERY_ASSESSMENT_OPTIONS, 0);
    }

    private String callApi(String projectId, List<RaisonElementInput> elements, List<String> options) {
        List<String> solutions = callApiSolutions(projectId, elements, options, 1);
        return solutions.isEmpty() ? null : solutions.get(0);
    }

    private List<String> callApiSolutions(String projectId, List<RaisonElementInput> elements, List<String> options, int limit) {
        String base = (config.baseUrl != null && !config.baseUrl.isEmpty())
                ? config.baseUrl : DEFAULT_BASE_URL;
        String path = String.format(RAISON_QUERY_PATH, projectId);
        String url = base + path;

        StringBuilder body = new StringBuilder("{\"elements\":[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) body.append(",");
            appendElementJson(body, projectId, elements.get(i));
        }
        body.append("],\"options\":[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) body.append(",");
            body.append("{\"id\":\"").append(JsonCodec.escape(resolveOptionId(projectId, options.get(i)))).append("\"}");
        }
        int effectiveLimit = limit > 0 ? limit : options.size();
        body.append("],\"limit\":").append(effectiveLimit).append("}");

        boolean debug = "1".equals(System.getenv("TRAINMIND_DEBUG"));
        if (debug) System.err.println("[API] POST " + url + " body=" + body);
        try {
            String response = httpPost(projectId, url, body.toString());
            List<String> solutions = parseSolutions(response, projectId, options);
            if (debug) System.err.println("[API] → solutions=" + solutions);
            return solutions;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            if (debug) System.err.println("[API] Erreur: " + e.getMessage());
            throw new IllegalStateException("Erreur API rAison: " + e.getMessage(), e);
        }
    }

    private void appendElementJson(StringBuilder body, String projectId, RaisonElementInput element) {
        body.append("{\"id\":\"")
                .append(JsonCodec.escape(resolveElementId(projectId, element.id)))
                .append("\",\"parameters\":[");
        for (int i = 0; i < element.parameters.size(); i++) {
            RaisonElementInput.Parameter p = element.parameters.get(i);
            if (i > 0) {
                body.append(",");
            }
            body.append("{\"name\":\"").append(JsonCodec.escape(p.name))
                    .append("\",\"value\":\"").append(JsonCodec.escape(p.value)).append("\"}");
        }
        body.append("]}");
    }

    private String resolveElementId(String projectId, String logicalId) {
        return resolveRaisonId(projectId, "ELEMENT", logicalId);
    }

    private String resolveOptionId(String projectId, String logicalId) {
        return resolveRaisonId(projectId, "OPTION", logicalId);
    }

    private String resolveRaisonId(String projectId, String kind, String logicalId) {
        String projectPrefix = projectId.equals(config.performanceProjectId) ? "PERFORMANCE" : "RECOVERY";
        String key = "RAISON_" + projectPrefix + "_" + kind + "_" + envKey(logicalId);
        String id = config.get(key, "");
        if (id.isEmpty()) {
            String genericKey = "RAISON_" + kind + "_" + envKey(logicalId);
            id = config.get(genericKey, "");
        }
        if (id.isEmpty()) {
            throw new IllegalStateException("ID rAison manquant pour " + kind.toLowerCase(Locale.ROOT)
                    + " '" + logicalId + "'. Ajoute " + key + "=OPT... dans TrainMind/.env");
        }
        return id;
    }

    private static String envKey(String logicalId) {
        return logicalId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private String httpPost(String projectId, String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        String apiKey = config.apiKeyForProject(projectId);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("x-api-key", apiKey);
        }
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        if (code >= 400) throw new IOException("HTTP " + code + ": " + sb);
        return sb.toString();
    }

    private List<String> parseSolutions(String json, String projectId, List<String> requestedOptions) {
        List<String> solutions = new ArrayList<>();
        if (json == null || json.isEmpty()) return solutions;
        Map<String, String> optionIds = new HashMap<>();
        for (String option : requestedOptions) {
            optionIds.put(resolveOptionId(projectId, option), option);
        }
        for (String object : splitTopLevelObjects(json)) {
            if (!object.contains("\"isSolution\":true") && !object.contains("\"isSolution\": true")) {
                continue;
            }
            String rawId = extractJsonString(object, "\"id\"");
            if (rawId != null) {
                solutions.add(optionIds.getOrDefault(rawId, rawId));
            }
        }
        return solutions;
    }

    private static List<String> splitTopLevelObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private static String extractJsonString(String json, String fieldName) {
        int field = json.indexOf(fieldName);
        if (field < 0) return null;
        int colon = json.indexOf(':', field + fieldName.length());
        if (colon < 0) return null;
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return null;
        int end = quote + 1;
        boolean escaped = false;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return json.substring(quote + 1, end);
            }
            end++;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Mapping AthleteContext → éléments rAison
    // ═══════════════════════════════════════════════════════════════════════

    private List<RaisonElementInput> buildPerformanceElements(AthleteContext ctx, boolean highRiskMode) {
        boolean conservativeMode = highRiskMode
                || Boolean.parseBoolean(ctx.get("risk_conservative_mode", "false"));
        List<RaisonElementInput> facts = new ArrayList<>(RaisonFactMapper.performanceFacts(ctx, conservativeMode));
        addPerformanceMeasureFacts(facts, ctx);
        return facts;
    }

    private void addPerformanceMeasureFacts(List<RaisonElementInput> facts, AthleteContext ctx) {
        addUnique(facts, RaisonElementInput.parameter("last_push_days", "P", Integer.toString(ctx.getInt("last_push_days", 3))));
        addUnique(facts, RaisonElementInput.parameter("last_pull_days", "U", Integer.toString(ctx.getInt("last_pull_days", 4))));
        addUnique(facts, RaisonElementInput.parameter("last_legs_days", "L", Integer.toString(ctx.getInt("last_legs_days", 5))));
        addUnique(facts, RaisonElementInput.parameter("chronic_km", "K", formatDouble(ctx.getDouble("chronic_km", 22))));
        addUnique(facts, RaisonElementInput.parameter("available_minutes", "M", Integer.toString(ctx.getInt("available_minutes", 60))));
        addUnique(facts, RaisonElementInput.parameter("acwr", "A", formatDouble(computeAcwr(ctx))));
    }

    private List<RaisonElementInput> buildRecoveryElements(AthleteContext ctx) {
        List<RaisonElementInput> facts = new ArrayList<>(RaisonFactMapper.recoveryFacts(ctx));
        addRecoveryMeasureFacts(facts, ctx);
        return facts;
    }

    private List<RaisonElementInput> buildRecoveryAssessmentElements(AthleteContext ctx, TrainingProposal proposal, String recoveryDecision) {
        List<RaisonElementInput> el = new ArrayList<>(buildRecoveryElements(ctx));
        if (recoveryDecision != null) {
            el.add(RaisonElementInput.atom(recoveryDecision));
        }
        String painZone = ctx.get("joint_pain_zone", "none");
        if (!"none".equals(painZone)) {
            el.add(RaisonElementInput.atom("pain_zone_" + painZone));
        }
        if (proposal.phase != null && proposal.phase.contains("level_2")) {
            el.add(RaisonElementInput.atom("proposal_level_2"));
        }
        el.add(RaisonElementInput.parameter("proposal_round", "RN", Integer.toString(proposal.round)));
        el.add(RaisonElementInput.parameter("proposal_rpe", "R", Integer.toString(proposal.rpe)));
        el.add(RaisonElementInput.parameter("proposal_volume", "V", Integer.toString(proposal.volumePercent)));
        String category = proposal.category == null ? "" : proposal.category;
        if (category.contains("running")) {
            el.add(RaisonElementInput.atom("proposal_running"));
        } else if ("complete_rest".equals(category) || category.contains("mobility")
                || category.contains("deload")) {
            el.add(RaisonElementInput.atom("proposal_recovery"));
        } else {
            el.add(RaisonElementInput.atom("proposal_strength"));
        }
        return el;
    }

    private void addRecoveryMeasureFacts(List<RaisonElementInput> facts, AthleteContext ctx) {
        addUnique(facts, RaisonElementInput.parameter("fatigue", "F", Integer.toString(ctx.getInt("fatigue", 4))));
        addUnique(facts, RaisonElementInput.parameter("sleep_hours", "S", formatDouble(ctx.getDouble("sleep_hours", 7.5))));
        addUnique(facts, RaisonElementInput.parameter("joint_pain", "J", Integer.toString(ctx.getInt("joint_pain", 0))));
        addUnique(facts, RaisonElementInput.parameter("soreness", "O", Integer.toString(ctx.getInt("soreness", 3))));
        addUnique(facts, RaisonElementInput.parameter("acwr", "A", formatDouble(computeAcwr(ctx))));
        addUnique(facts, RaisonElementInput.parameter("hrv", "H", ctx.get("hrv", "normal")));
    }

    private static void addUnique(List<RaisonElementInput> facts, RaisonElementInput fact) {
        for (RaisonElementInput existing : facts) {
            if (existing.signature().equals(fact.signature())) {
                return;
            }
        }
        facts.add(fact);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Calculs science sportive
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ACWR = charge aiguë (7 jours) / charge chronique (28 jours).
     * Zone sûre : 0.8 – 1.3. Au-delà de 1.5 : zone rouge blessure.
     */
    private double computeAcwr(AthleteContext ctx) {
        double stored = ctx.getDouble("acwr", 0);
        if (stored > 0) return stored;
        // Si non fourni, estimer depuis weekly_km / chronic_km
        double weekly  = ctx.getDouble("weekly_km", 0);
        double chronic = ctx.getDouble("chronic_km", 0);
        if (chronic > 0 && weekly > 0) return weekly / chronic;
        return 1.0;
    }

    /** Formule d'Epley : 1RM ≈ load * (1 + reps/30) */
    private double estimate1RM(double load, int reps) {
        if (load <= 0 || reps <= 0) return load;
        return load * (1.0 + reps / 30.0);
    }

    /** Charge cible pour un nombre de reps donné à partir du 1RM estimé */
    private double targetLoad(double oneRM, int targetReps) {
        if (oneRM <= 0 || targetReps <= 0) return 0;
        double raw = oneRM / (1.0 + targetReps / 30.0);
        return round25(raw);
    }

    private double round25(double x)  { return Math.round(x / 2.5) * 2.5; }
    private double round1(double x)   { return Math.round(x * 10.0) / 10.0; }

    // ═══════════════════════════════════════════════════════════════════════
    // Mapping réponse API → TrainingProposal
    // ═══════════════════════════════════════════════════════════════════════

    private String mapSessionToCategory(String apiOption, String target, AthleteContext ctx) {
        if (apiOption == null) {
            throw new IllegalStateException("Performance/session: réponse rAison absente.");
        }
        switch (apiOption) {
            case "strength_session":        return target + "_strength";
            case "hypertrophy_session":     return target + "_hypertrophy";
            case "explosive_power_session": return target + "_power";
            case "technique_session":       return target + "_technique";
            case "running_easy":            return "running_easy";
            case "running_tempo":           return "running_tempo";
            case "running_long":            return "running_long";
            case "running_intervals":       return "running_intervals";
            case "mobility_session":        return "mobility_session";
            case "deload_session":          return "deload_session";
            default: throw new IllegalStateException("Performance/session inconnue: " + apiOption);
        }
    }

    private String mapTarget(String apiTarget) {
        if (apiTarget == null) {
            throw new IllegalStateException("Performance/target: réponse rAison absente.");
        }
        if (apiTarget.startsWith("target_")) {
            return apiTarget.substring("target_".length());
        }
        throw new IllegalStateException("Performance/target inconnue: " + apiTarget);
    }

    private void applyPerformanceParameters(AthleteContext ctx, TrainingProposal proposal, boolean withRisk) {
        proposal.rpe = rpeValue(fetchPerformanceParameter(
                ctx, proposal, withRisk, PERFORMANCE_RPE_OPTIONS, "Performance/rpe"));
        proposal.volumePercent = volumeValue(fetchPerformanceParameter(
                ctx, proposal, withRisk, PERFORMANCE_VOLUME_OPTIONS, "Performance/volume"));
        String distanceOption = fetchPerformanceParameter(
                ctx, proposal, withRisk, PERFORMANCE_DISTANCE_OPTIONS, "Performance/distance");
        proposal.runningKm = runningDistanceFromHistory(ctx, proposal, distanceOption);
        proposal.intensity = intensityFromRpe(proposal.rpe);
    }

    private String sessionOptionFromCategory(String category) {
        if (category == null) {
            throw new IllegalStateException("Performance/category absente.");
        }
        if (category.endsWith("_strength")) return "strength_session";
        if (category.endsWith("_hypertrophy")) return "hypertrophy_session";
        if (category.endsWith("_power")) return "explosive_power_session";
        if (category.endsWith("_technique")) return "technique_session";
        return category;
    }

    private int rpeValue(String option) {
        if ("rpe_rest".equals(option)) return 1;
        if ("rpe_easy".equals(option)) return 5;
        if ("rpe_moderate".equals(option)) return 6;
        if ("rpe_hard".equals(option)) return 8;
        if ("rpe_very_hard".equals(option)) return 9;
        throw new IllegalStateException("Option RPE inconnue: " + option);
    }

    private int volumeValue(String option) {
        if ("volume_none".equals(option)) return 0;
        if ("volume_low".equals(option)) return 30;
        if ("volume_reduced".equals(option)) return 70;
        if ("volume_normal".equals(option)) return 100;
        throw new IllegalStateException("Option volume inconnue: " + option);
    }

    private double runningDistanceFromHistory(AthleteContext ctx, TrainingProposal proposal, String option) {
        if ("distance_none".equals(option)) {
            return 0;
        }
        if (proposal.category == null || !proposal.category.startsWith("running")) {
            return 0;
        }

        double chronicKm = ctx.getDouble("chronic_km", 22.0);
        double weeklyKm = ctx.getDouble("weekly_km", 0.0);
        double lastRunKm = ctx.getDouble("last_run_km", 0.0);
        double vma = ctx.getDouble("vma_kmh", 12.0);
        double availableMinutes = ctx.getDouble("available_minutes", 60.0);
        double acwr = computeAcwr(ctx);

        if (chronicKm <= 0) {
            chronicKm = weeklyKm > 0 ? weeklyKm : 22.0;
        }

        double share;
        if ("distance_short".equals(option)) {
            share = 0.22;
        } else if ("distance_medium".equals(option)) {
            share = 0.32;
        } else if ("distance_long".equals(option)) {
            share = 0.45;
        } else {
            throw new IllegalStateException("Option distance inconnue: " + option);
        }

        double distance = chronicKm * share;
        if (lastRunKm > 0) {
            distance = Math.min(distance, lastRunKm * progressionCap(proposal.category));
        }
        distance *= acwrAdjustment(acwr);
        distance *= Math.max(0, proposal.volumePercent) / 100.0;
        distance = Math.min(distance, availableDistanceCap(proposal.category, vma, availableMinutes));
        distance = Math.min(distance, categoryDistanceCap(proposal.category, chronicKm));
        return round1(Math.max(0, distance));
    }

    private String intensityFromRpe(int rpe) {
        if (rpe <= 1) return "rest";
        if (rpe <= 4) return "very_low";
        if (rpe <= 5) return "low";
        if (rpe <= 7) return "moderate";
        if (rpe == 8) return "high";
        return "very_high";
    }

    private void applyRecoveryAssessment(AthleteContext ctx, TrainingProposal proposal,
                                         NegotiationFeedback feedback, String apiRec) {
        List<String> solutions = fetchRecoveryAssessment(ctx, proposal, apiRec);
        feedback.riskLevel = "low";
        feedback.riskScore = 10;
        for (String solution : solutions) {
            if (solution.startsWith("risk_")) {
                applyRisk(feedback, solution);
            } else {
                addConstraint(feedback, solution);
            }
        }
        if (solutions.isEmpty()) {
            if (isSafeUncoveredProposal(ctx, proposal)) {
                feedback.riskLevel = "moderate";
                feedback.riskScore = 25;
                addFallbackSafetyConstraint(feedback, proposal);
                feedback.arguments.add("Recovery: aucun scénario rAison spécifique, contexte sain → garantie minimale demandée.");
                return;
            }
            feedback.riskLevel = "critical";
            feedback.riskScore = 70;
            addConstraint(feedback, "complete_rest_required");
            feedback.arguments.add("Recovery: rAison ne retourne aucune solution → repli de sécurité.");
        }
    }

    private void applyRisk(NegotiationFeedback feedback, String riskOption) {
        if ("risk_critical".equals(riskOption)) {
            feedback.riskLevel = "critical";
            feedback.riskScore = Math.max(feedback.riskScore, 70);
        } else if ("risk_high".equals(riskOption) && feedback.riskScore < 70) {
            feedback.riskLevel = "high";
            feedback.riskScore = Math.max(feedback.riskScore, 45);
        } else if ("risk_moderate".equals(riskOption) && feedback.riskScore < 45) {
            feedback.riskLevel = "moderate";
            feedback.riskScore = Math.max(feedback.riskScore, 25);
        } else if ("risk_low".equals(riskOption) && feedback.riskScore < 25) {
            feedback.riskLevel = "low";
            feedback.riskScore = Math.max(feedback.riskScore, 10);
        }
    }

    private static void addConstraint(NegotiationFeedback fb, String c) {
        if (!fb.constraints.contains(c)) fb.constraints.add(c);
    }

    private boolean isSafeUncoveredProposal(AthleteContext ctx, TrainingProposal proposal) {
        return ctx.getInt("joint_pain", 0) < 7
                && computeAcwr(ctx) <= 1.3
                && ctx.getInt("fatigue", 4) <= 6
                && ctx.getDouble("sleep_hours", 7.5) >= 6.5
                && proposal.rpe <= 8
                && !"complete_rest".equals(proposal.category);
    }

    private void addFallbackSafetyConstraint(NegotiationFeedback feedback, TrainingProposal proposal) {
        String category = proposal.category == null ? "" : proposal.category;
        if (proposal.volumePercent > 80) {
            addConstraint(feedback, "reduce_volume");
        } else if (proposal.rpe > 6) {
            addConstraint(feedback, "limit_rpe");
        } else if (category.startsWith("running")) {
            addConstraint(feedback, "distance_validated");
        } else if (category.contains("strength")
                || category.contains("hypertrophy")
                || category.contains("power")
                || category.contains("technique")) {
            addConstraint(feedback, "load_validated");
        } else {
            addConstraint(feedback, "stop_rule_defined");
        }
    }

    private void applyConstraint(AthleteContext ctx, TrainingProposal p, String c) {
        ConstraintApplier applier = constraintAppliers.get(c);
        if (applier == null) {
            addProposalConstraint(p, c);
            return;
        }
        applier.apply(ctx, p, c);
    }

    private void registerConstraintAppliers() {
        constraintAppliers.put("complete_rest_required", (ctx, p, c) -> {
            p.category = "complete_rest";
            p.target = "recovery";
            p.rpe = 1;
            p.volumePercent = 0;
            p.runningKm = 0;
            p.intensity = "rest";
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("mobility_only", (ctx, p, c) -> {
            p.category = "mobility_session";
            p.rpe = Math.min(p.rpe, 5);
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("low_intensity_only", (ctx, p, c) -> {
            p.intensity = "low";
            p.rpe = Math.min(p.rpe, 5);
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("limit_rpe", (ctx, p, c) -> {
            p.rpe = Math.min(p.rpe, 6);
            p.intensity = intensityFromRpe(p.rpe);
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("avoid_pain_zone", this::addProposalConstraint);
        constraintAppliers.put("reduce_volume", (ctx, p, c) -> {
            int previousVolume = p.volumePercent;
            p.volumePercent = Math.max(0, p.volumePercent - 20);
            if (p.runningKm > 0 && previousVolume > 0) {
                p.runningKm = round1(p.runningKm * p.volumePercent / previousVolume);
            }
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("distance_validated", (ctx, p, c) -> {
            if (p.runningKm > 0) {
                p.runningKm = validatedRunningDistance(ctx, p);
            }
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("load_validated", this::addProposalConstraint);
        constraintAppliers.put("rest_times_defined", this::addProposalConstraint);
        constraintAppliers.put("stop_rule_defined", this::addProposalConstraint);
        constraintAppliers.put("plan_validated", (ctx, p, c) -> {
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("max_rpe_5", (ctx, p, c) -> {
            p.rpe = Math.min(p.rpe, 5);
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("max_rpe_6", (ctx, p, c) -> {
            p.rpe = Math.min(p.rpe, 6);
            addProposalConstraint(p, c);
        });
        constraintAppliers.put("max_rpe_7", (ctx, p, c) -> {
            p.rpe = Math.min(p.rpe, 7);
            addProposalConstraint(p, c);
        });
    }

    private interface ConstraintApplier {
        void apply(AthleteContext ctx, TrainingProposal proposal, String constraint);
    }

    private void addProposalConstraint(AthleteContext ctx, TrainingProposal proposal, String constraint) {
        addProposalConstraint(proposal, constraint);
    }

    private void addProposalConstraint(TrainingProposal proposal, String constraint) {
        if (!proposal.constraints.contains(constraint)) {
            proposal.constraints.add(constraint);
        }
    }

    private double validatedRunningDistance(AthleteContext ctx, TrainingProposal proposal) {
        double current = proposal.runningKm;
        double chronicKm = ctx.getDouble("chronic_km", 22.0);
        if (chronicKm <= 0) {
            chronicKm = 22.0;
        }
        current = Math.min(current, categoryDistanceCap(proposal.category, chronicKm));
        current = Math.min(current, availableDistanceCap(
                proposal.category,
                ctx.getDouble("vma_kmh", 12.0),
                ctx.getDouble("available_minutes", 60.0)
        ));
        return round1(Math.max(0, current));
    }

    private double progressionCap(String category) {
        if (category == null) {
            return 1.10;
        }
        if (category.contains("long")) {
            return 1.20;
        }
        if (category.contains("interval")) {
            return 1.05;
        }
        return 1.15;
    }

    private double acwrAdjustment(double acwr) {
        if (acwr > 1.5) {
            return 0.0;
        }
        if (acwr > 1.3) {
            return 0.70;
        }
        if (acwr > 1.0) {
            return 1.0 - ((acwr - 1.0) / 0.30) * 0.15;
        }
        if (acwr < 0.8) {
            return 0.95;
        }
        return 1.0;
    }

    private double categoryDistanceCap(String category, double chronicKm) {
        double chronicCap = Math.max(3.0, chronicKm * 0.50);
        if (category == null) {
            return chronicCap;
        }
        if (category.contains("interval")) {
            return Math.min(chronicCap, 6.0);
        }
        if (category.contains("tempo")) {
            return Math.min(chronicCap, 9.0);
        }
        if (category.contains("easy")) {
            return Math.min(chronicCap, 8.0);
        }
        if (category.contains("long")) {
            return Math.min(chronicCap, 18.0);
        }
        return chronicCap;
    }

    private double availableDistanceCap(String category, double vma, double availableMinutes) {
        if (availableMinutes <= 0 || vma <= 0) {
            return 100.0;
        }
        double speedFactor = 0.70;
        if (category != null && category.contains("long")) {
            speedFactor = 0.68;
        } else if (category != null && category.contains("tempo")) {
            speedFactor = 0.78;
        } else if (category != null && category.contains("interval")) {
            speedFactor = 0.72;
        }
        return vma * speedFactor * (availableMinutes / 60.0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers TrainingProposal
    // ═══════════════════════════════════════════════════════════════════════

    private String restTimeFor(String cat) {
        if (cat == null) return "90s";
        if (cat.contains("strength") || cat.contains("power")) return "3-5min";
        if (cat.contains("hypertrophy"))            return "90s-2min";
        return "60-90s";
    }

    private String restTimeForAccessory(String cat) {
        if (cat != null && (cat.contains("strength") || cat.contains("power"))) return "2-3min";
        return "60-75s";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Programme d'exercices détaillé
    // ═══════════════════════════════════════════════════════════════════════

    private void fillExercises(AthleteContext ctx, TrainingProposal proposal) {
        String cat = proposal.category != null ? proposal.category : "";

        if (cat.contains("rest") || "complete_rest".equals(cat)) {
            proposal.exercises.add("Repos total — aucune activité physique");
            proposal.exercises.add("Hydratation : 2-3L d'eau, sommeil 8h minimum");
            return;
        }
        if (cat.contains("mobility") || "mobility_session".equals(cat)) {
            proposal.exercises.add("Foam rolling corps entier — 10 min (quadriceps, ischio, mollets, dorsaux)");
            proposal.exercises.add("Mobilité hanche : 90/90 stretch, fentes profondes — 3×30s chaque côté");
            proposal.exercises.add("Mobilité thoracique : cat-cow, rotations assises — 3×10 reps");
            proposal.exercises.add("Gainage léger : planche, bird-dog — 3×20s");
            return;
        }
        if ("deload_session".equals(cat)) {
            proposal.exercises.add("Mêmes exercices que la semaine passée, charges -30%, séries -1, reps identiques");
            proposal.exercises.add("Objectif : mouvement parfait, aucune douleur, RPE max 6");
            return;
        }
        if (cat.startsWith("running")) {
            fillRunningProgram(ctx, proposal);
            return;
        }
        // Musculation
        fillStrengthProgram(ctx, proposal);
    }

    private void fillRunningProgram(AthleteContext ctx, TrainingProposal proposal) {
        if (proposal.runningKm <= 0) {
            throw new IllegalStateException("Performance/distance: distance running non fournie par rAison.");
        }
        double km    = proposal.runningKm;
        double vma   = ctx.getDouble("vma_kmh", 12.0);
        int maxHr    = ctx.getInt("max_hr", 190);
        int restHr   = ctx.getInt("rest_hr", 60);

        if (proposal.category.contains("easy")) {
            // Zone 2 : 60-70% FCmax, allure conversationnelle
            int hrLow  = (int)(restHr + (maxHr - restHr) * 0.60);
            int hrHigh = (int)(restHr + (maxHr - restHr) * 0.70);
            double pace = 60.0 / (vma * 0.70);  // min/km à 70% VMA
            int paceMin = (int)pace;
            int paceSec = (int)((pace - paceMin) * 60);
            int durationMin = (int)(km / (vma * 0.70) * 60);
            proposal.exercises.add(String.format("Footing léger (zone 2) — %.1f km", km));
            proposal.exercises.add(String.format("Allure cible : %d'%02d\"/km  |  FC : %d-%d bpm (60-70%% FCmax)", paceMin, paceSec, hrLow, hrHigh));
            proposal.exercises.add("Test : tu dois pouvoir parler normalement en courant");
            proposal.exercises.add(String.format("Durée estimée : %d min", durationMin));
            if (km > 10) proposal.exercises.add("Hydratation : eau toutes les 20-30 min");

        } else if (proposal.category.contains("tempo")) {
            // Allure seuil lactique : 83-88% VMA, 80-85% FCmax, 20-35 min continu
            double warmKm   = Math.max(1.5, km * 0.20);
            double tempoKm  = Math.min(Math.max(2.5, km * 0.55), 12.0); // max 12 km de tempo
            double coolKm   = Math.max(1.0, km - warmKm - tempoKm);
            double tempoSpeed = vma * 0.85;
            int tempoHrLow  = (int)(restHr + (maxHr - restHr) * 0.80);
            int tempoHrHigh = (int)(restHr + (maxHr - restHr) * 0.88);
            double tempoPace = 60.0 / tempoSpeed;
            int tpMin = (int)tempoPace;
            int tpSec = (int)((tempoPace - tpMin) * 60);
            int tempoDuration = (int)(tempoKm / tempoSpeed * 60);
            proposal.exercises.add(String.format("Échauffement : %.1f km zone 1-2 + 4×80m progressifs (~%.0f min)", warmKm, warmKm/(vma*0.65)*60));
            proposal.exercises.add(String.format("Bloc seuil : %.1f km @ %d'%02d\"/km (%.0f min) — FC cible %d-%d bpm", tempoKm, tpMin, tpSec, (double)tempoDuration, tempoHrLow, tempoHrHigh));
            proposal.exercises.add("Sensation : respiration difficile mais contrôlée, impossible de parler plus de 3 mots");
            proposal.exercises.add(String.format("Retour au calme : %.1f km zone 1 (%d min)", coolKm, (int)(coolKm/(vma*0.60)*60)));
            proposal.exercises.add(String.format("Total : %.1f km", km));

        } else if (proposal.category.contains("long")) {
            // Sortie longue : 65-75% FCmax, base aérobie fondamentale
            int hrLow  = (int)(restHr + (maxHr - restHr) * 0.65);
            int hrHigh = (int)(restHr + (maxHr - restHr) * 0.75);
            double pace = 60.0 / (vma * 0.68);
            int paceMin = (int)pace;
            int paceSec = (int)((pace - paceMin) * 60);
            int durationMin = (int)(km / (vma * 0.68) * 60);
            proposal.exercises.add(String.format("Sortie longue — %.1f km", km));
            proposal.exercises.add(String.format("Allure : %d'%02d\"/km  |  FC : %d-%d bpm (65-75%% FCmax)", paceMin, paceSec, hrLow, hrHigh));
            proposal.exercises.add(String.format("Durée estimée : %d min", durationMin));
            if (durationMin >= 60) proposal.exercises.add("Nutrition : glucides toutes les 45 min (gel ou banane)");
            if (durationMin >= 90) proposal.exercises.add("Hydratation : 500 mL/h minimum");
            proposal.exercises.add("Ne pas accélérer en fin de sortie — rester en zone 2 tout du long");

        } else if (proposal.category.contains("interval")) {
            // Fractionné VO2max : 92-98% VMA, 90-95% FCmax
            double intSpeed = vma * 0.95;
            int nbReps;
            int repDist;
            int recupSec;
            if (km >= 9) {
                repDist = 1000; nbReps = (int)(km * 0.60); recupSec = 90;
            } else if (km >= 6) {
                repDist = 800; nbReps = (int)(km * 1000 * 0.60 / 800); recupSec = 75;
            } else {
                repDist = 400; nbReps = (int)(km * 1000 * 0.65 / 400); recupSec = 60;
            }
            nbReps = Math.max(4, Math.min(nbReps, 10));
            int repTime  = (int)(repDist / intSpeed * 3.6);
            int hrTarget = (int)(restHr + (maxHr - restHr) * 0.93);
            double intPace = 60.0 / intSpeed;
            int ipMin = (int)intPace; int ipSec = (int)((intPace - ipMin) * 60);
            proposal.exercises.add("Échauffement : 2 km footing zone 1 + 4×100m strides progressifs");
            proposal.exercises.add(String.format("Fractionné : %d × %dm @ %d'%02d\"/km (~%ds d'effort)", nbReps, repDist, ipMin, ipSec, repTime));
            proposal.exercises.add(String.format("Récupération : %ds trot lent entre chaque répétition  |  FC crête visée : ~%d bpm", recupSec, hrTarget));
            proposal.exercises.add("Sensation : respiration très difficile, impossible de parler");
            proposal.exercises.add("Retour au calme : 1.5 km zone 1 + étirements dynamiques 5 min");
            proposal.exercises.add(String.format("Total : ~%.1f km", km));
        }
    }

    private void fillStrengthProgram(AthleteContext ctx, TrainingProposal proposal) {
        String cat = proposal.category;

        // Déterminer schéma séries×reps selon type de séance
        int[] mainScheme = repScheme(cat, "main");        // [sets, reps]
        int[] secScheme  = repScheme(cat, "secondary");
        int[] accScheme  = repScheme(cat, "accessory");
        int[] finScheme  = repScheme(cat, "finisher");

        List<ExerciseLibrary.Exercise> exList = exerciseLibrary.selectStrengthExercises(ctx, proposal);
        if (exList == null || exList.isEmpty()) {
            proposal.exercises.add(proposal.target + " — programme " + cat);
            return;
        }

        boolean hasNoHistory = true;
        for (ExerciseLibrary.Exercise ex : exList) {
            if (ctx.hasExerciseHistory(ex.id)) { hasNoHistory = false; break; }
        }

        for (ExerciseLibrary.Exercise ex : exList) {
            boolean hasHistory = ctx.hasExerciseHistory(ex.id);
            double  histLoad   = ctx.getExerciseLoad(ex.id);
            int     histReps   = ctx.getExerciseReps(ex.id);

            int[] scheme   = "main".equals(ex.role) ? mainScheme
                           : "secondary".equals(ex.role) ? secScheme
                           : "accessory".equals(ex.role) ? accScheme : finScheme;
            int targetReps = scheme[1];
            int targetSets = adjustedSets(scheme[0], proposal.volumePercent);
            int rpe        = rpeForRole(proposal.rpe, ex.role);

            if (ex.loadable && hasHistory && histReps > 0) {
                // Historique complet : calcul Epley + progression
                double load = round25(targetLoad(estimate1RM(histLoad, histReps), targetReps)
                                      + progressionLoad(ctx, ex.id, cat));
                proposal.exercises.add(String.format(
                        "%s — %d×%d @ %.1f kg (RPE %d)", ex.name, targetSets, targetReps, load, rpe));

            } else if (ex.loadable && hasHistory) {
                // Charge connue mais sans reps detaillees
                proposal.exercises.add(String.format(
                        "%s — %d×%d @ %.1f kg (RPE %d)", ex.name, targetSets, targetReps, histLoad, rpe));

            } else if (ex.loadable && ex.defaultLoadKg > 0) {
                // Premiere seance : poids de depart conservateur
                proposal.exercises.add(String.format(
                        "%s — %d×%d @ %.1f kg (RPE %d)  ← poids de depart suggere",
                        ex.name, targetSets, targetReps, ex.defaultLoadKg, rpe));

            } else if (ex.loadable) {
                proposal.exercises.add(String.format(
                        "%s — %d×%d au poids du corps (RPE %d)", ex.name, targetSets, targetReps, rpe));

            } else {
                proposal.exercises.add(String.format(
                        "%s — %d×%d (RPE %d)", ex.name, targetSets, targetReps, rpe));
            }
        }

        if (hasNoHistory) {
            proposal.exercises.add("→ Premiere seance : commencez leger, ajustez a la seance suivante.");
        }

        // Résumé programme
        String restMain = restTimeFor(cat);
        String restAcc  = restTimeForAccessory(cat);
        proposal.exercises.add(String.format("→ Repos : %s exercices principaux / %s accessoires", restMain, restAcc));
    }

    private int adjustedSets(int baseSets, int volumePercent) {
        if (volumePercent >= 100) {
            return baseSets;
        }
        if (volumePercent <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(baseSets * volumePercent / 100.0f));
    }

    /**
     * Schéma [sets, reps] selon le type de séance, le rôle de l'exercice et la phase.
     * Basé sur : Schoenfeld (hypertrophie), Wendler 5/3/1 (force), Potach (puissance).
     */
    private int[] repScheme(String cat, String role) {
        if (cat == null) return new int[]{3, 12};

        if (cat.contains("strength")) {
            // Force : haute intensité, faible volume, longues récups
            switch (role) {
                case "main":      return new int[]{5, 3};   // 5×3 @ 87-92% 1RM
                case "secondary": return new int[]{4, 4};   // 4×4 @ 82-87% 1RM
                case "accessory": return new int[]{3, 6};   // 3×6 @ 75-80% 1RM
                default:          return new int[]{3, 10};  // finisher léger
            }
        }
        if (cat.contains("power")) {
            // Puissance : vitesse d'exécution maximale, charges modérées (55-75% 1RM)
            switch (role) {
                case "main":      return new int[]{5, 3};   // 5×3 explosif @ 60-70% 1RM
                case "secondary": return new int[]{4, 4};   // 4×4 @ 55-65% 1RM
                case "accessory": return new int[]{3, 8};   // 3×8 contrôlé
                default:          return new int[]{3, 10};
            }
        }
        if (cat.contains("technique")) {
            // Technique : charges légères (55-65% 1RM), focus qualité du mouvement
            return new int[]{4, 5};
        }
        // Hypertrophie (défaut) — Schoenfeld: 6-20 reps, RIR 1-3
        // Volume optimal : 10-20 séries/groupe musculaire/semaine
        switch (role) {
            case "main":      return new int[]{4, 10};  // 4×10 @ 70-75% 1RM, RPE 7-8
            case "secondary": return new int[]{4, 12};  // 4×12 @ 65-70% 1RM
            case "accessory": return new int[]{3, 15};  // 3×15 @ 60-65% 1RM
            default:          return new int[]{3, 15};  // finisher : pump, métabolique
        }
    }

    private int rpeForRole(int base, String role) {
        if ("accessory".equals(role) || "finisher".equals(role)) return Math.max(6, base - 1);
        return base;
    }

    /**
     * Progression basée sur RPE historique ET niveau athlète.
     * Novice  : progression linéaire agressive (+2.5 kg si RPE ≤ 8).
     * Intermédiaire : double progression (reps d'abord, puis charge).
     * Avancé  : vague — +2.5 si RPE ≤ 6, 0 si 7-8, -2.5 si > 8.
     * Phase deload : toujours -30% (géré en amont via volumePercent).
     */
    private double progressionLoad(AthleteContext ctx, String exerciseId, String cat) {
        int lastRpe   = ctx.getExerciseRpe(exerciseId);
        int lastReps  = ctx.getExerciseReps(exerciseId);
        String age    = ctx.get("training_age", "intermediate");
        String phase  = ctx.get("cycle_phase", "accumulation");

        if ("deload".equals(phase)) return 0;

        boolean isStrength = cat != null && cat.contains("strength");
        boolean isPower    = cat != null && cat.contains("power");

        if ("novice".equals(age) || "debutant".equals(age)) {
            // Progression linéaire : +2.5 kg dès que l'exercice était faisable
            if (lastRpe <= 8) return 2.5;
            return -2.5;
        }
        if ("advanced".equals(age) || "expert".equals(age)) {
            // Progression lente : n'augmenter que si vraiment facile
            if (lastRpe <= 6) return isStrength || isPower ? 5.0 : 2.5;
            if (lastRpe <= 7) return 2.5;
            if (lastRpe > 8)  return -2.5;
            return 0;
        }
        // Intermédiaire (défaut) : progression conservatrice
        if (lastRpe < 7)  return 2.5;
        if (lastRpe > 8)  return -2.5;
        // RPE 7-8 en force/puissance : augmenter légèrement si reps dans la fourchette haute
        if ((isStrength || isPower) && lastReps >= 5) return 2.5;
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers affichage
    // ═══════════════════════════════════════════════════════════════════════

    public static String friendlySessionType(String cat) {
        if (cat == null) return "séance adaptée";
        if (cat.contains("strength"))   return "force maximale";
        if (cat.contains("hypertrophy")) return "hypertrophie";
        if (cat.contains("power"))      return "puissance explosive";
        if (cat.contains("technique"))  return "technique";
        if (cat.contains("easy"))       return "footing facile (zone 2)";
        if (cat.contains("tempo"))      return "tempo / seuil";
        if (cat.contains("long"))       return "sortie longue";
        if (cat.contains("interval"))   return "fractionné VO2max";
        if (cat.contains("mobility"))   return "mobilité / gainage";
        if (cat.contains("deload"))     return "déload (-40% volume)";
        if (cat.contains("rest"))       return "repos complet";
        return cat.replace("_", " ");
    }
}
