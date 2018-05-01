package fr.upem.net.client;

import fr.upem.net.client.ClientMatou.ContextClient;
import fr.upem.net.message.Message;

@FunctionalInterface
public interface ClientFunction {
	/**
	 * Apply the method
	 * @param msg the Message
	 * @param client the ClientMatou
	 * @param ctc The ContextClient
	 */
	public void apply(Message msg, ClientMatou client, ContextClient ctc);
}
