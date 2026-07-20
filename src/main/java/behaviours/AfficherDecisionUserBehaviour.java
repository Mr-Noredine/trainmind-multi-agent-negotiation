import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.io.IOException;
import java.time.LocalDate;

public class AfficherDecisionUserBehaviour extends OneShotBehaviour {
    private static final String PROPOSAL_MARKER = "\n---PROPOSAL---\n";
    private final TrainingHistoryWriter historyWriter = new TrainingHistoryWriter();

    public AfficherDecisionUserBehaviour(Agent agent) {
        super(agent);
    }

    public void action() {
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchProtocol(NegotiationConstants.PROTOCOL)
        );
        ACLMessage msg = myAgent.blockingReceive(template);
        if (msg.getPerformative() != ACLMessage.INFORM) return;

        String content = msg.getContent();
        int markerIdx = content.indexOf(PROPOSAL_MARKER);

        if (markerIdx < 0) {
            System.out.println(content);
            return;
        }

        String displayText = content.substring(0, markerIdx);
        String serialized  = content.substring(markerIdx + PROPOSAL_MARKER.length());

        System.out.println(displayText);

        TrainingProposal proposal = TrainingProposal.parse(serialized);
        autoSave(proposal);
    }

    private void autoSave(TrainingProposal proposal) {
        try {
            String cat = proposal.category != null ? proposal.category : "";

            if (cat.contains("rest") || cat.contains("repos")) {
                historyWriter.appendEntry(LocalDate.now(),
                    "recovery", cat, "complete_rest", 0, 0, 1, 0);
                printSaved("Repos complet enregistre.");
                return;
            }

            if (cat.contains("mobility") || cat.contains("deload_session")) {
                historyWriter.appendEntry(LocalDate.now(),
                    "recovery", cat, "mobility", 0, 0, proposal.rpe, 0);
                printSaved("Seance mobilite enregistree.");
                return;
            }

            if (cat.startsWith("running")) {
                historyWriter.appendEntry(LocalDate.now(),
                    "running", cat, "run", 0, 0, proposal.rpe, proposal.runningKm);
                printSaved(String.format("Course enregistree : %.1f km, RPE %d.", proposal.runningKm, proposal.rpe));
                return;
            }

            // Musculation : une ligne par exercice
            int saved = 0;
            for (String line : proposal.exercises) {
                if (line.startsWith("→") || line.startsWith("->")) continue;

                ExerciseParsed ex = parseExerciseLine(line);
                if (ex == null) continue;

                historyWriter.appendEntry(LocalDate.now(),
                    proposal.target, cat, ex.name, ex.load, ex.reps, ex.rpe > 0 ? ex.rpe : proposal.rpe, 0);
                saved++;
            }

            if (saved > 0) {
                printSaved(String.format("%d exercice(s) enregistre(s) — %s / %s.",
                    saved, proposal.target, cat));
            } else {
                historyWriter.appendEntry(LocalDate.now(),
                    proposal.target, cat, "seance", 0, 0, proposal.rpe, 0);
                printSaved("Seance enregistree.");
            }

        } catch (IOException e) {
            System.out.println("[USER] Impossible d'enregistrer la seance : " + e.getMessage());
        }
    }

    private void printSaved(String detail) {
        System.out.println("  → " + detail + " (src/main/data/training_history.csv)");
    }

    // ═══ Parsing d'une ligne d'exercice ════════════════════════════════════

    private static class ExerciseParsed {
        String name; double load; int reps; int rpe;
    }

    /**
     * Parse lines of the form:
     *   "Bench press — 4×10 @ 65,0 kg (RPE 7)"
     *   "Romanian deadlift — 5×4 @ 70.0 kg (RPE 8)"
     *   "Pull-ups — 3×8 (RPE 7)"
     */
    private static ExerciseParsed parseExerciseLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;

        ExerciseParsed ex = new ExerciseParsed();

        // Name: everything before " — " (em dash) or " - "
        int dashIdx = line.indexOf(" — ");
        if (dashIdx < 0) dashIdx = line.indexOf(" - ");
        if (dashIdx < 0) return null;
        ex.name = line.substring(0, dashIdx).trim();

        String rest = line.substring(dashIdx + 3);

        // Reps: pattern N×M or NxM — take M (target reps per set)
        java.util.regex.Matcher repsMatcher =
            java.util.regex.Pattern.compile("\\d+[×x](\\d+)").matcher(rest);
        if (repsMatcher.find()) {
            ex.reps = parseInt(repsMatcher.group(1), 0);
        }

        // Load: number before " kg" — handle both "," and "." as decimal separator
        java.util.regex.Matcher loadMatcher =
            java.util.regex.Pattern.compile("([\\d]+[.,][\\d]+|\\d+)\\s*kg").matcher(rest);
        if (loadMatcher.find()) {
            ex.load = parseDouble(loadMatcher.group(1).replace(",", "."), 0);
        }

        // RPE: number in "(RPE N)"
        java.util.regex.Matcher rpeMatcher =
            java.util.regex.Pattern.compile("\\(RPE\\s*(\\d+)\\)").matcher(rest);
        if (rpeMatcher.find()) {
            ex.rpe = parseInt(rpeMatcher.group(1), 0);
        }

        return ex;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
}
