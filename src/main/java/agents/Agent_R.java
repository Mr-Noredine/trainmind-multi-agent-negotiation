import jade.core.Agent;

public class Agent_R extends Agent {

    protected void setup() {
        TrainMindConfig config = TrainMindConfig.load();
        RaisonService raisonService = RaisonServiceFactory.create(config);
        AgentDirectory.register(this, NegotiationConstants.RECOVERY_SERVICE, getLocalName());
        System.out.println("[RECOVERY] Agent pret. Mode=RAISON");
        addBehaviour(new RecoveryNegotiationResponderBehaviour(this, raisonService));
    }

    protected void takeDown() {
        AgentDirectory.deregister(this);
    }
}
