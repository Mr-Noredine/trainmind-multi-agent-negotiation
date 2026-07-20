import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class PerformanceNegotiationFSMBehaviour extends FSMBehaviour {
    private static final String INIT = "INIT";
    private static final String PROPOSE = "PROPOSE";
    private static final String WAIT_RECOVERY = "WAIT_RECOVERY";
    private static final String REFINE = "REFINE";
    private static final String FINALIZE = "FINALIZE";
    private static final String FAIL = "FAIL";

    private static final int OK = 0;
    private static final int ACCEPTED = 1;
    private static final int NEEDS_REFINEMENT = 2;
    private static final int ERROR = 3;

    private final RaisonService raisonService;
    private final ACLMessage managerRequest;
    private final AthleteContext context;
    private final String conversationId;

    private AID recoveryAgent;
    private TrainingProposal proposal;
    private NegotiationFeedback lastFeedback;
    private int completedRounds;
    private String failureReason = "Erreur negotiation";

    public PerformanceNegotiationFSMBehaviour(Agent agent, RaisonService raisonService, ACLMessage managerRequest) {
        super(agent);
        this.raisonService = raisonService;
        this.managerRequest = managerRequest;
        this.context = AthleteContext.parse(managerRequest.getContent());
        this.conversationId = managerRequest.getConversationId();

        registerFirstState(new InitState(agent), INIT);
        registerState(new ProposeState(agent), PROPOSE);
        registerState(new WaitRecoveryState(agent), WAIT_RECOVERY);
        registerState(new RefineState(agent), REFINE);
        registerLastState(new FinalizeState(agent), FINALIZE);
        registerLastState(new FailState(agent), FAIL);

        registerTransition(INIT, PROPOSE, OK);
        registerTransition(INIT, FAIL, ERROR);
        registerDefaultTransition(PROPOSE, WAIT_RECOVERY);
        registerTransition(WAIT_RECOVERY, FINALIZE, ACCEPTED);
        registerTransition(WAIT_RECOVERY, REFINE, NEEDS_REFINEMENT);
        registerTransition(WAIT_RECOVERY, FAIL, ERROR);
        registerTransition(REFINE, PROPOSE, OK);
        registerTransition(REFINE, FAIL, ERROR);
    }

    private class InitState extends OneShotBehaviour {
        private int exitCode = OK;

        InitState(Agent agent) {
            super(agent);
        }

        public void action() {
            try {
                proposal = raisonService.initialPerformanceProposal(context);
                recoveryAgent = AgentDirectory.findFirst(myAgent,
                        NegotiationConstants.RECOVERY_SERVICE, NegotiationConstants.RECOVERY_AGENT);
                UserGlossary.print();
                System.out.println("========== Dialogue de negociation ==========");
            } catch (RuntimeException e) {
                failureReason = e.getMessage();
                exitCode = ERROR;
            }
        }

        public int onEnd() {
            return exitCode;
        }
    }

    private class ProposeState extends OneShotBehaviour {
        ProposeState(Agent agent) {
            super(agent);
        }

        public void action() {
            int round = completedRounds + 1;
            proposal.round = round;
            System.out.println();
            System.out.println("Round " + round);
            System.out.println(NegotiationDialogueFormatter.performanceProposal(proposal, context));

            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.addReceiver(recoveryAgent);
            cfp.setProtocol(NegotiationConstants.PROTOCOL);
            cfp.setLanguage(NegotiationConstants.LANGUAGE);
            cfp.setOntology(NegotiationConstants.ONTOLOGY);
            cfp.setConversationId(conversationId);
            cfp.setReplyWith(conversationId + "-cfp-" + round);
            cfp.addUserDefinedParameter(NegotiationConstants.CONTEXT_PARAMETER,
                    managerRequest.getContent());
            cfp.setContent(proposal.serialize());
            myAgent.send(cfp);
        }
    }

    private class WaitRecoveryState extends OneShotBehaviour {
        private int exitCode = ERROR;

        WaitRecoveryState(Agent agent) {
            super(agent);
        }

        public void action() {
            String expectedReply = conversationId + "-cfp-" + proposal.round;
            MessageTemplate replyTemplate = MessageTemplate.and(
                    MessageTemplate.MatchProtocol(NegotiationConstants.PROTOCOL),
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId(conversationId),
                            MessageTemplate.and(
                                    MessageTemplate.MatchSender(recoveryAgent),
                                    MessageTemplate.MatchInReplyTo(expectedReply))));
            ACLMessage answer = myAgent.blockingReceive(
                    replyTemplate, NegotiationConstants.MESSAGE_TIMEOUT_MS);
            if (answer == null) {
                failureReason = "Recovery n'a pas repondu au round " + proposal.round
                        + " dans le delai imparti.";
                exitCode = ERROR;
                return;
            }

            completedRounds = proposal.round;

            if (answer.getPerformative() == ACLMessage.FAILURE) {
                failureReason = "Recovery: " + answer.getContent();
                exitCode = ERROR;
                return;
            }

            if (answer.getPerformative() != ACLMessage.PROPOSE) {
                failureReason = "Recovery a envoye une performative inattendue: "
                        + ACLMessage.getPerformative(answer.getPerformative());
                exitCode = ERROR;
                return;
            }

            lastFeedback = NegotiationFeedback.parse(answer.getContent());
            if (lastFeedback.round != proposal.round) {
                failureReason = "Recovery a repondu pour le round " + lastFeedback.round
                        + " au lieu du round " + proposal.round + ".";
                exitCode = ERROR;
                return;
            }
            System.out.println(NegotiationDialogueFormatter.recoveryAnswer(lastFeedback, context));
            if (lastFeedback.accepted) {
                exitCode = ACCEPTED;
                return;
            }
            if (completedRounds >= NegotiationConstants.MAX_ROUNDS) {
                failureReason = "Aucun accord apres " + completedRounds
                        + " rounds; la derniere proposition reste refusee par Recovery.";
                exitCode = ERROR;
                return;
            }
            exitCode = NEEDS_REFINEMENT;
        }

        public int onEnd() {
            return exitCode;
        }
    }

    private class RefineState extends OneShotBehaviour {
        private int exitCode = OK;

        RefineState(Agent agent) {
            super(agent);
        }

        public void action() {
            try {
                proposal = raisonService.refinePerformanceProposal(
                        context, proposal, lastFeedback, completedRounds + 1);
            } catch (RuntimeException e) {
                failureReason = e.getMessage();
                exitCode = ERROR;
            }
        }

        public int onEnd() {
            return exitCode;
        }
    }

    private class FinalizeState extends OneShotBehaviour {
        FinalizeState(Agent agent) {
            super(agent);
        }

        public void action() {
            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            accept.addReceiver(recoveryAgent);
            accept.setProtocol(NegotiationConstants.PROTOCOL);
            accept.setLanguage(NegotiationConstants.LANGUAGE);
            accept.setOntology(NegotiationConstants.ONTOLOGY);
            accept.setConversationId(conversationId);
            accept.setReplyWith(conversationId + "-accept-final");
            accept.setContent(proposal.serialize());
            myAgent.send(accept);

            MessageTemplate confirmationTemplate = MessageTemplate.and(
                    MessageTemplate.MatchProtocol(NegotiationConstants.PROTOCOL),
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId(conversationId),
                            MessageTemplate.and(
                                    MessageTemplate.MatchSender(recoveryAgent),
                                    MessageTemplate.MatchInReplyTo(accept.getReplyWith()))));
            ACLMessage confirmation = myAgent.blockingReceive(
                    confirmationTemplate, NegotiationConstants.MESSAGE_TIMEOUT_MS);
            if (confirmation == null) {
                sendFailureToManager("Recovery n'a pas confirme l'accord final dans le delai imparti.");
                return;
            }
            if (confirmation.getPerformative() != ACLMessage.INFORM) {
                sendFailureToManager("Recovery a refuse la confirmation finale: "
                        + confirmation.getContent());
                return;
            }

            System.out.println(NegotiationDialogueFormatter.finalAgreement(proposal, completedRounds));
            System.out.println("=============================================");

            String finalDecision = TrainingPlanFormatter.format(proposal, completedRounds)
                    + "\n---PROPOSAL---\n" + proposal.serialize();
            ACLMessage result = new ACLMessage(ACLMessage.INFORM);
            result.addReceiver(new AID(NegotiationConstants.MANAGER_AGENT, AID.ISLOCALNAME));
            result.setProtocol(NegotiationConstants.PROTOCOL);
            result.setLanguage(NegotiationConstants.LANGUAGE);
            result.setOntology(NegotiationConstants.ONTOLOGY);
            result.setConversationId(conversationId);
            result.setInReplyTo(managerRequest.getReplyWith());
            result.setContent(finalDecision);
            myAgent.send(result);
        }
    }

    private class FailState extends OneShotBehaviour {
        FailState(Agent agent) {
            super(agent);
        }

        public void action() {
            sendFailureToManager(failureReason);
        }
    }

    private void sendFailureToManager(String reason) {
        ACLMessage failure = new ACLMessage(ACLMessage.FAILURE);
        failure.addReceiver(new AID(NegotiationConstants.MANAGER_AGENT, AID.ISLOCALNAME));
        failure.setProtocol(NegotiationConstants.PROTOCOL);
        failure.setLanguage(NegotiationConstants.LANGUAGE);
        failure.setOntology(NegotiationConstants.ONTOLOGY);
        failure.setConversationId(conversationId);
        failure.setInReplyTo(managerRequest.getReplyWith());
        failure.setContent(reason);
        myAgent.send(failure);
        System.out.println("[PERFORMANCE] Echec negociation: " + reason);
    }
}
