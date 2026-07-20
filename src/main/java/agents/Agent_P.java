import jade.core.Agent;

public class Agent_P extends Agent {

    protected void setup() {
        TrainMindConfig config = TrainMindConfig.load();
        RaisonService raisonService = RaisonServiceFactory.create(config);
        AgentDirectory.register(this, NegotiationConstants.PERFORMANCE_SERVICE, getLocalName());
        System.out.println("[PERFORMANCE] Agent pret. Mode=RAISON");
        addBehaviour(new PerformanceNegotiationInitiatorBehaviour(this, raisonService));
    }

    protected void takeDown() {
        AgentDirectory.deregister(this);
    }
}
