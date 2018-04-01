package fr.upem.net.tcp;

@FunctionalInterface
public interface ClientFunction {

	public void apply(BodyParser bp);
}
