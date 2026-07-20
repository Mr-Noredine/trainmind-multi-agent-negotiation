import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class SaisirContexteUserBehaviour extends OneShotBehaviour {
    public SaisirContexteUserBehaviour(Agent agent) {
        super(agent);
    }

    public void action() {
        waitForStartupLogs();
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════");
        System.out.println("  TrainMind — Bilan du jour");
        System.out.println("  Repondez aux questions ou appuyez Entree pour garder la valeur par defaut.");
        System.out.println("  Mode avance : collez directement un contexte key=value a la 1ere question.");
        System.out.println("══════════════════════════════════════════════════════");
        System.out.println();

        String contexte = "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            contexte = buildContext(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (contexte == null) contexte = "";
        contexte = contexte.trim();

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        String conversationId = "trainmind-user-" + System.currentTimeMillis();
        msg.addReceiver(new AID(NegotiationConstants.MANAGER_AGENT, AID.ISLOCALNAME));
        msg.setProtocol(NegotiationConstants.PROTOCOL);
        msg.setLanguage(NegotiationConstants.LANGUAGE);
        msg.setOntology(NegotiationConstants.ONTOLOGY);
        msg.setConversationId(conversationId);
        msg.setReplyWith(conversationId + "-context");
        msg.setContent(contexte);
        myAgent.send(msg);
        System.out.println();
        System.out.println("[Contexte envoye] " + contexte);
    }

    private String buildContext(BufferedReader reader) throws IOException {
        System.out.println("  Objectif principal :");
        System.out.println("    force    : soulever le plus lourd possible (force maximale)");
        System.out.println("    masse    : prendre du volume musculaire (seances hypertrophie)");
        System.out.println("    course   : course a pied, peu importe la distance ou l'objectif");
        System.out.print("  Votre choix  (defaut : course) : ");
        System.out.flush();
        String first = safeRead(reader);

        // Mode avance : contexte key=value colle directement
        if (first.contains("=")) {
            return first;
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("goal",              translateGoal(defaultValue(first, "course")));
        values.put("cycle_phase",       translatePhase(ask(reader,
            "  Phase du cycle        [accumulation / intensification / pic / decharge]", "accumulation")));
        values.put("sleep_hours",       ask(reader,
            "  Sommeil cette nuit    (en heures, ex. 7.5)", "7.5"));
        values.put("fatigue",           ask(reader,
            "  Fatigue               (0 = aucune  →  10 = epuisement total)", "4"));
        values.put("soreness",          ask(reader,
            "  Courbatures           (0 = aucune  →  10 = tres intenses)", "3"));
        values.put("soreness_zone",     translateZoneMuscle(ask(reader,
            "  Zone courbaturee      [aucune / poitrine-epaules / dos-biceps / jambes]", "aucune")));
        values.put("joint_pain",        ask(reader,
            "  Douleur articulaire   (0 = aucune  →  10 = severe)", "0"));
        values.put("joint_pain_zone",   translateZoneArticulaire(ask(reader,
            "  Zone douloureuse      [aucune / epaule / coude / poignet / dos / genou / cheville / hanche]", "aucune")));
        values.put("hrv",               translateHrv(ask(reader,
            "  Forme nerveuse (HRV)  [faible / normale / elevee]", "normale")));
        values.put("acwr",              ask(reader,
            "  Charge hebdo recente  (ACWR, ex. 1.0 = normal, 1.5 = surcharge)", "1.0"));
        values.put("available_minutes", ask(reader,
            "  Temps disponible      (en minutes)", "60"));

        return serialize(values);
    }

    // ═══ Traductions FR → EN ════════════════════════════════════════════════

    private String translateGoal(String raw) {
        String v = raw.toLowerCase().trim();
        if (v.equals("force")        || v.equals("strength"))    return "strength";
        if (v.equals("masse")        || v.equals("hypertrophie") || v.equals("hypertrophy")) return "hypertrophy";
        if (v.equals("course")       || v.equals("running")
                || v.startsWith("run") || v.contains("km"))      return "running";
        return raw;
    }

    private String translatePhase(String raw) {
        switch (raw.toLowerCase().trim()) {
            case "accumulation":                     return "accumulation";
            case "intensification":                  return "intensification";
            case "pic":          case "peaking":     return "peaking";
            case "decharge":     case "deload":      return "deload";
            default:                                 return raw;
        }
    }

    private String translateHrv(String raw) {
        switch (raw.toLowerCase().trim()) {
            case "faible":  case "bas":   case "low":    return "low";
            case "normale": case "normal":               return "normal";
            case "elevee":  case "haut":  case "high":   return "high";
            default:                                     return raw;
        }
    }

    private String translateZoneMuscle(String raw) {
        String v = raw.toLowerCase().trim();
        if (v.equals("aucune") || v.equals("none") || v.isEmpty()) return "none";
        if (v.contains("poitrine") || v.contains("pecho") || v.contains("epaule")
                || v.contains("push"))                              return "push";
        if (v.contains("dos") || v.contains("bicep") || v.contains("pull")) return "pull";
        if (v.contains("jambe") || v.contains("leg"))               return "legs";
        return v; // passe-partout (si l'utilisateur tape en anglais)
    }

    private String translateZoneArticulaire(String raw) {
        String v = raw.toLowerCase().trim();
        if (v.equals("aucune") || v.equals("none") || v.isEmpty()) return "none";
        if (v.startsWith("ep"))  return "shoulder";  // epaule
        if (v.startsWith("co"))  return "elbow";     // coude
        if (v.startsWith("po"))  return "wrist";     // poignet
        if (v.startsWith("do"))  return "back";      // dos
        if (v.startsWith("ge"))  return "knee";      // genou
        if (v.startsWith("ch"))  return "ankle";     // cheville
        if (v.startsWith("ha"))  return "hip";       // hanche
        // Accepter aussi les valeurs anglaises directement
        switch (v) {
            case "shoulder": return "shoulder";
            case "elbow":    return "elbow";
            case "wrist":    return "wrist";
            case "back":     return "back";
            case "knee":     return "knee";
            case "ankle":    return "ankle";
            case "hip":      return "hip";
            default:         return v;
        }
    }

    // ═══ Helpers I/O ════════════════════════════════════════════════════════

    private String ask(BufferedReader reader, String label, String defaultValue) throws IOException {
        System.out.print(label + "  (defaut : " + defaultValue + ") : ");
        System.out.flush();
        return defaultValue(safeRead(reader), defaultValue);
    }

    private String safeRead(BufferedReader reader) throws IOException {
        String value = reader.readLine();
        return value == null ? "" : value.trim();
    }

    private String defaultValue(String value, String defaultValue) {
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    private String serialize(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private void waitForStartupLogs() {
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
