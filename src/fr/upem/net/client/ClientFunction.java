package fr.upem.net.client;

import fr.upem.net.parser.BodyParser;

@FunctionalInterface
public interface ClientFunction {

	public void apply(BodyParser bp, ClientMatou client);
}
