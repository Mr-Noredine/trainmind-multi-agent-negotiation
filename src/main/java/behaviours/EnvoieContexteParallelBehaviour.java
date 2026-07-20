import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.lang.acl.ACLMessage;

public class EnvoieContexteParallelBehaviour extends ParallelBehaviour {

    public EnvoieContexteParallelBehaviour(final Agent_Manager manager) {
        super(manager, ParallelBehaviour.WHEN_ALL);

        addSubBehaviour(new OneShotBehaviour(manager) {
            public void action() {
                ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                AID recoveryAgent = AgentDirectory.findFirst(myAgent,
                        NegotiationConstants.RECOVERY_SERVICE, NegotiationConstants.RECOVERY_AGENT);
                inform.addReceiver(recoveryAgent);
                inform.setProtocol(NegotiationConstants.PROTOCOL);
                inform.setLanguage(NegotiationConstants.LANGUAGE);
                inform.setOntology(NegotiationConstants.ONTOLOGY);
                inform.setConversationId(manager.conversationId);
                inform.setReplyWith(manager.conversationId + "-ctx-recovery");
                inform.setContent(manager.contexte);
                System.out.println("[MANAGER] Contexte envoye a "
                        + recoveryAgent.getLocalName() + " pour analyse recovery.");
                myAgent.send(inform);
            }
        });

        addSubBehaviour(new OneShotBehaviour(manager) {
            public void action() {
                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                AID performanceAgent = AgentDirectory.findFirst(myAgent,
                        NegotiationConstants.PERFORMANCE_SERVICE, NegotiationConstants.PERFORMANCE_AGENT);
                request.addReceiver(performanceAgent);
                request.setProtocol(NegotiationConstants.PROTOCOL);
                request.setLanguage(NegotiationConstants.LANGUAGE);
                request.setOntology(NegotiationConstants.ONTOLOGY);
                request.setConversationId(manager.conversationId);
                request.setReplyWith(manager.conversationId + "-request-performance");
                request.setContent(manager.contexte);
                System.out.println("[MANAGER] Demande de negociation envoyee a "
                        + performanceAgent.getLocalName() + ".");
                myAgent.send(request);
            }
        });
    }
}
