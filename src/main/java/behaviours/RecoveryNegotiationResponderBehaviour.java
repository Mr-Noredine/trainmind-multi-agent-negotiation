import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.HashMap;
import java.util.Map;

public class RecoveryNegotiationResponderBehaviour extends CyclicBehaviour {
    private final RaisonService raisonService;
    private final Map<Integer, MessageHandler> handlers = new HashMap<>();
    private final Map<String, AthleteContext> contextsByConversation = new HashMap<>();
    private final Map<String, String> acceptedPlansByConversation = new HashMap<>();

    public RecoveryNegotiationResponderBehaviour(Agent agent, RaisonService raisonService) {
        super(agent);
        this.raisonService = raisonService;
        handlers.put(ACLMessage.INFORM, this::rememberContext);
        handlers.put(ACLMessage.CFP, this::startRecoveryFsm);
        handlers.put(ACLMessage.ACCEPT_PROPOSAL, this::confirmFinalAgreement);
    }

    @Override
    public void action() {
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchProtocol(NegotiationConstants.PROTOCOL),
                MessageTemplate.and(
                        MessageTemplate.MatchLanguage(NegotiationConstants.LANGUAGE),
                        MessageTemplate.MatchOntology(NegotiationConstants.ONTOLOGY)));
        ACLMessage msg = myAgent.receive(template);
        if (msg == null) {
            block();
            return;
        }

        MessageHandler handler = handlers.get(msg.getPerformative());
        if (handler != null) {
            handler.handle(msg);
        }
    }

    private void rememberContext(ACLMessage msg) {
        if (msg.getConversationId() != null) {
            contextsByConversation.put(msg.getConversationId(), AthleteContext.parse(msg.getContent()));
        }
    }

    private void startRecoveryFsm(ACLMessage msg) {
        String conversationId = msg.getConversationId();
        String embeddedContext = msg.getUserDefinedParameter(NegotiationConstants.CONTEXT_PARAMETER);
        AthleteContext context = embeddedContext == null
                ? contextsByConversation.get(conversationId)
                : AthleteContext.parse(embeddedContext);
        if (context == null) {
            ACLMessage failure = msg.createReply(ACLMessage.FAILURE);
            failure.setContent("Contexte athlete absent pour la conversation " + conversationId + ".");
            myAgent.send(failure);
            return;
        }
        contextsByConversation.put(conversationId, context);
        myAgent.addBehaviour(new RecoveryCfpFSMBehaviour(
                myAgent, raisonService, context, msg, this::rememberReview));
    }

    private void rememberReview(ACLMessage cfp, TrainingProposal proposal, NegotiationFeedback feedback) {
        if (feedback.accepted && cfp.getConversationId() != null) {
            acceptedPlansByConversation.put(cfp.getConversationId(), proposal.serialize());
        }
    }

    private void confirmFinalAgreement(ACLMessage msg) {
        ACLMessage confirmation = msg.createReply();
        String conversationId = msg.getConversationId();
        String acceptedPlan = acceptedPlansByConversation.remove(conversationId);
        if (acceptedPlan != null && acceptedPlan.equals(msg.getContent())) {
            confirmation.setPerformative(ACLMessage.INFORM);
            confirmation.setContent("Accord final confirme par Recovery.");
            contextsByConversation.remove(conversationId);
        } else {
            confirmation.setPerformative(ACLMessage.FAILURE);
            confirmation.setContent("Le plan final ne correspond pas a une proposition validee par Recovery.");
        }
        myAgent.send(confirmation);
    }

    private interface MessageHandler {
        void handle(ACLMessage message);
    }
}
