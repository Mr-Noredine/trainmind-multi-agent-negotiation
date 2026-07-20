import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class RecoveryCfpFSMBehaviour extends FSMBehaviour {
    private static final String ANALYZE = "ANALYZE";
    private static final String SEND = "SEND";

    private final RaisonService raisonService;
    private final AthleteContext context;
    private final ACLMessage cfp;
    private final ReviewListener reviewListener;
    private ACLMessage reply;

    public RecoveryCfpFSMBehaviour(Agent agent, RaisonService raisonService,
                                   AthleteContext context, ACLMessage cfp,
                                   ReviewListener reviewListener) {
        super(agent);
        this.raisonService = raisonService;
        this.context = context;
        this.cfp = cfp;
        this.reviewListener = reviewListener;

        registerFirstState(new AnalyzeState(agent), ANALYZE);
        registerLastState(new SendState(agent), SEND);
        registerDefaultTransition(ANALYZE, SEND);
    }

    private class AnalyzeState extends OneShotBehaviour {
        AnalyzeState(Agent agent) {
            super(agent);
        }

        public void action() {
            reply = cfp.createReply();
            reply.setProtocol(NegotiationConstants.PROTOCOL);
            reply.setLanguage(NegotiationConstants.LANGUAGE);
            reply.setOntology(NegotiationConstants.ONTOLOGY);
            try {
                TrainingProposal proposal = TrainingProposal.parse(cfp.getContent());
                NegotiationFeedback feedback = raisonService.reviewRecoveryProposal(
                        context, proposal, proposal.round);
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(feedback.serialize());
                reviewListener.onReviewed(cfp, proposal, feedback);
            } catch (RuntimeException e) {
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setContent(e.getMessage());
                System.out.println("[RECOVERY] Echec analyse: " + e.getMessage());
            }
        }
    }

    private class SendState extends OneShotBehaviour {
        SendState(Agent agent) {
            super(agent);
        }

        public void action() {
            myAgent.send(reply);
        }
    }

    public interface ReviewListener {
        void onReviewed(ACLMessage cfp, TrainingProposal proposal, NegotiationFeedback feedback);
    }
}
