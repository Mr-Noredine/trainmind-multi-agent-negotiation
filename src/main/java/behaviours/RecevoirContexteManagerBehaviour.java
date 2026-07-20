import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class RecevoirContexteManagerBehaviour extends OneShotBehaviour {
    private final Agent_Manager manager;

    public RecevoirContexteManagerBehaviour(Agent_Manager manager) {
        super(manager);
        this.manager = manager;
    }

    public void action() {
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchProtocol(NegotiationConstants.PROTOCOL)
        );
        ACLMessage msg = myAgent.blockingReceive(template);
        if (msg.getPerformative() == ACLMessage.INFORM) {
            String contexteJournee = msg.getContent();
            manager.contexte = manager.athleteRepository.buildCompleteContext(contexteJournee);
            manager.conversationId = msg.getConversationId();
            System.out.println("[MANAGER] Contexte recu, profil charge. Conversation: " + manager.conversationId);
        }
    }
}
