import jade.core.Agent;
import jade.core.behaviours.SequentialBehaviour;

public class Agent_Manager extends Agent {
    String contexte = "";
    String choix = "";
    String conversationId = "";
    final AthleteRepository athleteRepository = new AthleteRepository();

    protected void setup() {
        System.out.println("[MANAGER] Agent pret: " + getLocalName());

        SequentialBehaviour sq = new SequentialBehaviour();
        sq.addSubBehaviour(new RecevoirContexteManagerBehaviour(this));
        sq.addSubBehaviour(new EnvoieContexteParallelBehaviour(this));
        sq.addSubBehaviour(new RecevoirReponsesArbitrageBehaviour(this));
        sq.addSubBehaviour(new EnvoieDecisionFinaleManagerBehaviour(this));
        addBehaviour(sq);
    }
}
