import jade.core.Agent;
import jade.core.behaviours.SequentialBehaviour;

public class Agent_User extends Agent {
    private static final String SEP = "==================================================";

    protected void setup() {
        System.out.println(SEP);
        System.out.println("[USER] Agent pret: " + getLocalName());
        System.out.println(SEP);

        SequentialBehaviour sq = new SequentialBehaviour();
        sq.addSubBehaviour(new SaisirContexteUserBehaviour(this));
        sq.addSubBehaviour(new AfficherDecisionUserBehaviour(this));
        addBehaviour(sq);
    }
}
