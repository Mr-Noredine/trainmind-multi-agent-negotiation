public class RaisonServiceFactory {
    private RaisonServiceFactory() {}

    public static RaisonService create(TrainMindConfig config) {
        return new RaisonApiService(config);
    }
}
