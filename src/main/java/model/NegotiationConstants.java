public final class NegotiationConstants {
    public static final String PROTOCOL = "trainmind-iterated-contract-net";
    public static final String LANGUAGE = "JSON";
    public static final String ONTOLOGY = "TrainMind-rAison";
    public static final String PERFORMANCE_SERVICE = "trainmind-performance";
    public static final String RECOVERY_SERVICE = "trainmind-recovery";
    public static final String MANAGER_AGENT = "agent_manager";
    public static final String USER_AGENT = "agent_user";
    public static final String PERFORMANCE_AGENT = "agent_p";
    public static final String RECOVERY_AGENT = "agent_r";
    public static final String CONTEXT_PARAMETER = "trainmind-context";
    public static final int MAX_ROUNDS = 14;
    public static final int MIN_ARGUMENT_ROUNDS = 5;
    public static final long MESSAGE_TIMEOUT_MS = 60_000L;

    private NegotiationConstants() {
    }
}
