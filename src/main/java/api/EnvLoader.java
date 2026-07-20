import java.io.*;
import java.util.*;

public class EnvLoader {
    String path;

    public EnvLoader(String path) {
        this.path = path;
    }
    public Map<String, String> load() throws IOException {
        Map<String, String> env = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String ligne;
            while ((ligne = br.readLine()) != null) {
                ligne = ligne.trim();
                if (ligne.isEmpty() || ligne.startsWith("#")) continue;
                String[] parts = ligne.split("=", 2);
                if (parts.length == 2) {
                    env.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return env;
    }
}