package tornado.meta;

import java.util.*;
import tornado.api.Event;
import tornado.common.enums.Access;
import tornado.meta.domain.DomainTree;

public class Meta {

    private final Map<Class<?>, Object> providers;
    protected final List<Event> profiles;
    protected DomainTree domain;
    protected Access[] argumentsAccess;

    public Meta(int numParameters) {
        providers = new HashMap<>();
        profiles = new ArrayList<>(8192);
        argumentsAccess = new Access[numParameters];
        Arrays.fill(argumentsAccess, Access.NONE);
    }

    public boolean hasDomain() {
        return domain != null;
    }

    public DomainTree getDomain() {
        return domain;
    }

    public void setDomain(final DomainTree value) {
        domain = value;
    }

    public void addProvider(final Class<?> providerClass, final Object provider) {
        providers.put(providerClass, provider);
    }

    public void addProfile(Event event) {
        profiles.add(event);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProvider(final Class<T> providerClass) {
        return (T) providers.get(providerClass);
    }

    public boolean hasProvider(final Class<?> providerClass) {
        return providers.containsKey(providerClass);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(String.format("meta : providers=%d\n", providers.size()));
        for (final Class<?> key : providers.keySet()) {
            sb.append(String.format("     : type=%s, value=%s\n",
                    key.getName(), providers.get(key).toString()));
        }
        return sb.toString().trim();
    }

    public Collection<Object> providers() {
        return providers.values();
    }

    public List<Event> getProfiles() {
        return profiles;
    }

    public boolean isParallel() {
        return hasDomain() && domain.getDepth() > 0;
    }

    public Access[] getArgumentsAccess() {
        return argumentsAccess;
    }

}
