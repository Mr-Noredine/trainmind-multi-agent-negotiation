import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class RecevoirReponsesArbitrageBehaviour extends OneShotBehaviour {
    private final Agent_Manager manager;

    public RecevoirReponsesArbitrageBehaviour(Agent_Manager manager) {
        super(manager);
        this.manager = manager;
    }

    public void action() {
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchProtocol(NegotiationConstants.PROTOCOL),
                MessageTemplate.and(
                        MessageTemplate.MatchConversationId(manager.conversationId),
                        MessageTemplate.MatchInReplyTo(
                                manager.conversationId + "-request-performance"))
        );
        ACLMessage msg = myAgent.blockingReceive(template);
        if (msg.getPerformative() == ACLMessage.INFORM) {
            manager.choix = msg.getContent();
            System.out.println("[MANAGER] Decision finale negociee recue depuis "
                    + msg.getSender().getLocalName() + ".");
        } else {
            manager.choix = formatFailure(msg.getContent());
            System.out.println("[MANAGER] Echec recu: " + msg.getContent());
        }
    }

    private String formatFailure(String raw) {
        String detail = raw == null ? "Erreur inconnue." : raw.trim();
        StringBuilder out = new StringBuilder();
        out.append("\n═════════════════════════════════════════════════════\n");
        out.append("  TRAINMIND — DECISION IMPOSSIBLE\n");
        out.append("═════════════════════════════════════════════════════\n\n");
        out.append("  Le systeme n'a pas produit de programme fiable.\n\n");
        out.append("  Cause technique : ").append(detail).append("\n\n");
        if (detail.contains("aucune réponse rAison")) {
            out.append("  Interpretation : aucun scenario rAison ne couvre ce contexte.\n");
            out.append("  Action : ajouter un scenario dans le projet rAison concerne,\n");
            out.append("  puis ouvrir /run pour appliquer les changements.\n");
        } else if (detail.contains("HTTP 403") || detail.toLowerCase().contains("api key")) {
            out.append("  Interpretation : cle API invalide ou utilisee sur le mauvais projet.\n");
            out.append("  Action : verifier TrainMind/.env et les cles Performance/Recovery.\n");
        } else if (detail.contains("HTTP 429") || detail.toLowerCase().contains("too_many_requests")) {
            out.append("  Interpretation : quota API rAison atteint.\n");
            out.append("  Action : attendre le reset du quota ou utiliser une autre cle autorisee.\n");
        } else if (detail.toLowerCase().contains("timed out")) {
            out.append("  Interpretation : l'API rAison n'a pas repondu assez vite.\n");
            out.append("  Action : relancer le meme contexte. Si cela persiste, verifier la connexion\n");
            out.append("  ou attendre quelques minutes avant de retester.\n");
        } else if (detail.contains("HTTP")) {
            out.append("  Interpretation : erreur API rAison.\n");
            out.append("  Action : verifier la connexion, le deploiement /run et les IDs .env.\n");
        } else {
            out.append("  Action : relancer avec un contexte de demo connu ou verifier rAison.\n");
        }
        out.append("\n═════════════════════════════════════════════════════\n");
        return out.toString();
    }
}
