package tornado.common;

public class NamedEvent extends TimedEvent {

	private final String	name;

	public NamedEvent(final String name, final long t0, final long t1) {
		super(t0, t1);
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
