public final class DialoguePerformatives {
    public static final String CFP = "cfp";
    public static final String PROPOSE = "propose";
    public static final String REFUSE = "refuse";
    public static final String REJECT_PROPOSAL = "reject-proposal";
    public static final String QUERY_IF = "query-if";
    public static final String INFORM = "inform";
    public static final String ACCEPT_PROPOSAL = "accept-proposal";
    public static final String FAILURE = "failure";

    public static String act(String performative, String contentType) {
        return "[" + performative + "(" + contentType + ")]";
    }

    private DialoguePerformatives() {
    }
}
