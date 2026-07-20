public interface RaisonService {
    TrainingProposal initialPerformanceProposal(AthleteContext context);

    TrainingProposal refinePerformanceProposal(AthleteContext context, TrainingProposal previous, NegotiationFeedback feedback, int nextRound);

    NegotiationFeedback reviewRecoveryProposal(AthleteContext context, TrainingProposal proposal, int round);
}
