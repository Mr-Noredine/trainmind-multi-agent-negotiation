import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class EnvoieDecisionFinaleManagerBehaviour extends OneShotBehaviour {
    private final Agent_Manager manager;

    public EnvoieDecisionFinaleManagerBehaviour(Agent_Manager manager) {
        super(manager);
        this.manager = manager;
    }

    public void action() {
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        inform.addReceiver(new AID(NegotiationConstants.USER_AGENT, AID.ISLOCALNAME));
        inform.setProtocol(NegotiationConstants.PROTOCOL);
        inform.setLanguage(NegotiationConstants.LANGUAGE);
        inform.setOntology(NegotiationConstants.ONTOLOGY);
        inform.setConversationId(manager.conversationId);
        inform.setContent(manager.choix);
        myAgent.send(inform);
        System.out.println("[MANAGER] Decision transmise a Agent_User.");
    }
}
