import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class TrainMindConfig {

    public final String apiKey;
    public final String performanceApiKey;
    public final String recoveryApiKey;
    public final String baseUrl;
    public final String performanceProjectId;
    public final String recoveryProjectId;
    public final Map<String, String> env;

    private TrainMindConfig(String apiKey, String performanceApiKey, String recoveryApiKey, String baseUrl,
                            String performanceProjectId, String recoveryProjectId,
                            Map<String, String> env) {
        this.apiKey = apiKey;
        this.performanceApiKey = performanceApiKey;
        this.recoveryApiKey = recoveryApiKey;
        this.baseUrl = baseUrl;
        this.performanceProjectId = performanceProjectId;
        this.recoveryProjectId = recoveryProjectId;
        this.env = Collections.unmodifiableMap(env);
    }

    public static TrainMindConfig load() {
        try {
            Map<String, String> env = new EnvLoader(".env").load();

            String modeStr = env.getOrDefault("RAISON_MODE", "raison").toLowerCase().trim();
            if (!"raison".equals(modeStr)) {
                throw new IllegalArgumentException("RAISON_MODE invalide: " + modeStr
                        + " (seule la plateforme rAison est supportée dans cette version finale)");
            }
            String baseUrl = env.getOrDefault("RAISON_BASE_URL", "https://api.ai-raison.com");
            String defaultPerfId = "PRJ31575";
            String defaultRecId  = "PRJ31625";

            String apiKey            = env.getOrDefault("RAISON_API_KEY", "");
            String performanceApiKey = env.getOrDefault("RAISON_PERFORMANCE_API_KEY", apiKey);
            String recoveryApiKey    = env.getOrDefault("RAISON_RECOVERY_API_KEY", apiKey);
            String performanceProjectId = env.getOrDefault("RAISON_PERFORMANCE_PROJECT_ID", defaultPerfId);
            String recoveryProjectId    = env.getOrDefault("RAISON_RECOVERY_PROJECT_ID",    defaultRecId);

            if (performanceApiKey.trim().isEmpty()) {
                throw new IllegalStateException("Cle API rAison Performance manquante dans .env");
            }
            if (recoveryApiKey.trim().isEmpty()) {
                throw new IllegalStateException("Cle API rAison Recovery manquante dans .env");
            }

            System.out.println("[CONFIG] RAISON_MODE=RAISON"
                    + " | baseUrl=" + (baseUrl.isEmpty() ? "(none)" : baseUrl)
                    + " | perf=" + performanceProjectId + " | rec=" + recoveryProjectId);

            return new TrainMindConfig(apiKey, performanceApiKey, recoveryApiKey,
                    baseUrl, performanceProjectId, recoveryProjectId, env);

        } catch (IOException e) {
            throw new IllegalStateException("TrainMind/.env introuvable: la version finale nécessite la plateforme rAison.", e);
        }
    }

    public String get(String key, String defaultValue) {
        return env.getOrDefault(key, defaultValue);
    }

    public String apiKeyForProject(String projectId) {
        if (projectId != null && projectId.equals(performanceProjectId)) {
            return performanceApiKey;
        }
        if (projectId != null && projectId.equals(recoveryProjectId)) {
            return recoveryApiKey;
        }
        return apiKey;
    }
}
