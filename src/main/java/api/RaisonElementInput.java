import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RaisonElementInput {
    public final String id;
    public final List<Parameter> parameters;

    private RaisonElementInput(String id, List<Parameter> parameters) {
        this.id = id;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public static RaisonElementInput atom(String id) {
        return new RaisonElementInput(id, Collections.emptyList());
    }

    public static RaisonElementInput parameter(String id, String name, String value) {
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter(name, value));
        return new RaisonElementInput(id, parameters);
    }

    public String signature() {
        if (parameters.isEmpty()) {
            return id;
        }
        if (parameters.size() == 1) {
            return id + "=" + parameters.get(0).value;
        }
        StringBuilder fact = new StringBuilder(id).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                fact.append(",");
            }
            fact.append(parameters.get(i).value);
        }
        fact.append(")");
        return fact.toString();
    }

    public static class Parameter {
        public final String name;
        public final String value;

        public Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
