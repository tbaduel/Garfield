package fr.upem.net.client;

import fr.upem.net.client.ClientMatou.ContextClient;
import fr.upem.net.message.Message;

@FunctionalInterface
public interface ClientFunction {

	public void apply(Message msg, ClientMatou client, ContextClient ctc);
}
