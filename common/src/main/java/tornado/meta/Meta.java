package tornado.meta;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Meta {

	private final Map<Class<?>, Object>	providers;

	public Meta() {
		providers = new HashMap<Class<?>, Object>();
	}

	public void addProvider(final Class<?> providerClass, final Object provider) {
		providers.put(providerClass, provider);
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
}
