import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class PerformanceNegotiationInitiatorBehaviour extends CyclicBehaviour {
    private final RaisonService raisonService;

    public PerformanceNegotiationInitiatorBehaviour(Agent agent, RaisonService raisonService) {
        super(agent);
        this.raisonService = raisonService;
    }

    @Override
    public void action() {
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.MatchProtocol(NegotiationConstants.PROTOCOL)
        );
        ACLMessage msg = myAgent.receive(template);
        if (msg == null) {
            block();
            return;
        }

        myAgent.addBehaviour(new PerformanceNegotiationFSMBehaviour(myAgent, raisonService, msg));
    }
}
