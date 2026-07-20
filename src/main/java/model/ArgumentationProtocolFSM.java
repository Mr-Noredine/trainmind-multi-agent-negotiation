import java.util.Iterator;

public final class ArgumentationProtocolFSM {
    private ArgumentationProtocolFSM() {
    }

    public static boolean acceptIfProtocolComplete(
            AthleteContext context,
            TrainingProposal proposal,
            NegotiationFeedback feedback,
            boolean brainAccepted) {

        pruneSatisfiedConstraints(proposal, feedback);

        if ("complete_rest".equals(proposal.category)) {
            add(feedback, "plan_validated");
            feedback.arguments.add("ProtocolFSM: accept-proposal(rest_plan)");
            return true;
        }

        if (feedback.constraints.contains("complete_rest_required")) {
            keepOnly(feedback, "complete_rest_required");
            feedback.arguments.add("ProtocolFSM: reject-proposal(critical_risk)");
            return false;
        }

        if (hasOpenConstraint(feedback)) {
            keepNextOpenConstraint(feedback);
            feedback.arguments.add("ProtocolFSM: propose(safety_counter_constraints)");
            return false;
        }

        if (!brainAccepted) {
            feedback.arguments.add("ProtocolFSM: reject-proposal(no_plan_validation)");
            return false;
        }

        if (feedback.round < NegotiationConstants.MIN_ARGUMENT_ROUNDS
                && addNextUsefulGuarantee(context, proposal, feedback)) {
            keepNextOpenConstraint(feedback);
            feedback.arguments.add("ProtocolFSM: query-if(next_safety_guarantee)");
            return false;
        }

        if (!proposal.constraints.contains("stop_rule_defined")
                && add(feedback, "stop_rule_defined")) {
            keepNextOpenConstraint(feedback);
            feedback.arguments.add("ProtocolFSM: query-if(stop_rule_defined)");
            return false;
        }

        add(feedback, "plan_validated");
        feedback.arguments.add("ProtocolFSM: accept-proposal(safe_plan)");
        return true;
    }

    private static void pruneSatisfiedConstraints(TrainingProposal proposal, NegotiationFeedback feedback) {
        Iterator<String> it = feedback.constraints.iterator();
        while (it.hasNext()) {
            String constraint = it.next();
            if (!"plan_validated".equals(constraint) && isSatisfied(proposal, constraint)) {
                it.remove();
            }
        }
    }

    private static boolean isSatisfied(TrainingProposal proposal, String constraint) {
        if (proposal.constraints.contains(constraint)) {
            return true;
        }
        if ("reduce_volume".equals(constraint)) {
            return proposal.volumePercent <= 80;
        }
        if ("limit_rpe".equals(constraint) || "max_rpe_6".equals(constraint)) {
            return proposal.rpe <= 6;
        }
        if ("max_rpe_7".equals(constraint)) {
            return proposal.rpe <= 7;
        }
        if ("low_intensity_only".equals(constraint)) {
            return proposal.rpe <= 5 || "low".equals(proposal.intensity) || "rest".equals(proposal.intensity);
        }
        if ("avoid_pain_zone".equals(constraint)) {
            return "recovery".equals(proposal.target) || "complete_rest".equals(proposal.category);
        }
        return false;
    }

    private static boolean hasOpenConstraint(NegotiationFeedback feedback) {
        for (String constraint : feedback.constraints) {
            if (!"plan_validated".equals(constraint) && !"risk_low".equals(constraint)) {
                return true;
            }
        }
        return false;
    }

    private static void keepOnly(NegotiationFeedback feedback, String selected) {
        Iterator<String> it = feedback.constraints.iterator();
        while (it.hasNext()) {
            String constraint = it.next();
            if (!selected.equals(constraint)) {
                it.remove();
            }
        }
    }

    private static void keepNextOpenConstraint(NegotiationFeedback feedback) {
        String selected = selectNextConstraint(feedback);
        Iterator<String> it = feedback.constraints.iterator();
        while (it.hasNext()) {
            String constraint = it.next();
            if ("plan_validated".equals(constraint) || "risk_low".equals(constraint)) {
                it.remove();
            } else if (selected == null || !selected.equals(constraint)) {
                it.remove();
            }
        }
    }

    private static String selectNextConstraint(NegotiationFeedback feedback) {
        String[] priority = {
            "complete_rest_required",
            "avoid_pain_zone",
            "reduce_volume",
            "low_intensity_only",
            "limit_rpe",
            "distance_validated",
            "load_validated",
            "rest_times_defined",
            "stop_rule_defined"
        };
        for (String candidate : priority) {
            if (feedback.constraints.contains(candidate)) {
                return candidate;
            }
        }
        for (String constraint : feedback.constraints) {
            if (!"plan_validated".equals(constraint) && !"risk_low".equals(constraint)) {
                return constraint;
            }
        }
        return null;
    }

    private static boolean addNextUsefulGuarantee(
            AthleteContext context,
            TrainingProposal proposal,
            NegotiationFeedback feedback) {

        if (proposal.volumePercent > 80 && !proposal.constraints.contains("reduce_volume")) {
            return add(feedback, "reduce_volume");
        }
        if (proposal.rpe > 6 && !proposal.constraints.contains("limit_rpe")) {
            return add(feedback, "limit_rpe");
        }
        if (context.getInt("joint_pain", 0) >= 4
                && !"recovery".equals(proposal.target)
                && !proposal.constraints.contains("avoid_pain_zone")) {
            return add(feedback, "avoid_pain_zone");
        }
        if (isRunning(proposal) && !proposal.constraints.contains("distance_validated")) {
            return add(feedback, "distance_validated");
        }
        if (isStrengthLike(proposal) && !proposal.constraints.contains("load_validated")) {
            return add(feedback, "load_validated");
        }
        if (isStrengthLike(proposal) && !proposal.constraints.contains("rest_times_defined")) {
            return add(feedback, "rest_times_defined");
        }
        if (!proposal.constraints.contains("stop_rule_defined")) {
            return add(feedback, "stop_rule_defined");
        }
        return false;
    }

    private static boolean isRunning(TrainingProposal proposal) {
        return proposal.category != null && proposal.category.startsWith("running");
    }

    private static boolean isStrengthLike(TrainingProposal proposal) {
        if (proposal.category == null) {
            return false;
        }
        return proposal.category.contains("strength")
                || proposal.category.contains("hypertrophy")
                || proposal.category.contains("power")
                || proposal.category.contains("technique");
    }

    private static boolean add(NegotiationFeedback feedback, String constraint) {
        if (!feedback.constraints.contains(constraint)) {
            feedback.constraints.add(constraint);
            return true;
        }
        return false;
    }
}
