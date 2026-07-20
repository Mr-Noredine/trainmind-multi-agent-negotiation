import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public final class AgentDirectory {
    private AgentDirectory() {
    }

    public static void register(Agent agent, String serviceType, String serviceName) {
        DFAgentDescription description = new DFAgentDescription();
        description.setName(agent.getAID());

        ServiceDescription service = new ServiceDescription();
        service.setType(serviceType);
        service.setName(serviceName);
        service.addOntologies(NegotiationConstants.ONTOLOGY);
        service.addLanguages(NegotiationConstants.LANGUAGE);
        description.addServices(service);

        try {
            DFService.register(agent, description);
        } catch (FIPAException e) {
            throw new IllegalStateException("DF registration failed for " + serviceType, e);
        }
    }

    public static void deregister(Agent agent) {
        try {
            DFService.deregister(agent);
        } catch (FIPAException ignored) {
        }
    }

    public static AID findFirst(Agent agent, String serviceType, String fallbackLocalName) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription service = new ServiceDescription();
        service.setType(serviceType);
        template.addServices(service);

        try {
            DFAgentDescription[] results = DFService.search(agent, template);
            if (results.length > 0) {
                return results[0].getName();
            }
        } catch (FIPAException e) {
            System.out.println("[" + agent.getLocalName() + "] DF lookup failed for "
                    + serviceType + ": " + e.getMessage());
        }
        return new AID(fallbackLocalName, AID.ISLOCALNAME);
    }
}
