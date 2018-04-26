package fr.upem.net.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import fr.upem.net.message.Message;
import fr.upem.net.message.MessageIp;
import fr.upem.net.message.MessageOneString;
import fr.upem.net.message.MessageTwoString;
import fr.upem.net.other.Opcode;


public class HubServ {
	private static final Charset UTF8 = Charset.forName("utf-8");
	private final HashMap<Opcode, ServerFunction> map = new HashMap<>();
	static private int BUFFER_SIZE = 1_024;
	public Opcode opcodeAction;
	ByteBuffer headerToSend = ByteBuffer.allocate(5);

	public HubServ() {
		map.put(Opcode.LOGIN, this::login);
		map.put(Opcode.SIGNUP, this::signup);
		map.put(Opcode.MESSAGE, this::message);
		map.put(Opcode.REQUEST, this::requestPrivate);
		map.put(Opcode.WHISP_OK, this::whispOk);
		map.put(Opcode.WHISP_REFUSED, this::whispRefused);

	}

	public ByteBuffer ServerExecute(Message msg, ServerMatou server, SocketChannel sc) throws IOException {
		if (msg == null) {
			return null;
		}
		Opcode opcode = Opcode.valueOfId(msg.getOp());
		ServerFunction fnt = map.get(opcode);
		if (fnt == null || (server == null && msg.getOp() != Opcode.MESSAGE.op)) { // added for whisper
			return null;
		}
		
		return fnt.apply(msg, server, sc);
		/*
		 * switch(opcode) { case SIGNUP: signup(msg, server, sc); break;
		 * 
		 * case LOGIN: login(msg, server, sc); break;
		 * 
		 * case MESSAGE: message(msg, server,sc ); break;
		 * 
		 * default: break;
		 * 
		 * }
		 */

	}

	private ByteBuffer signup(Message msg, ServerMatou server, SocketChannel sc) {
		MessageTwoString message = (MessageTwoString) msg;
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		System.out.println("Signin...");
		String name = message.str1;
		if (server.map.containsValue(name)) { // Username already used
			bb.putInt(Opcode.SIGNUP_ERR.op);
		} else { // Username not used
			bb.putInt(Opcode.SIGNUP_OK.op);
			//System.out.println("ADDED: " + Opcode.SIGNUP_OK);
			try {
				server.map.put(sc.getRemoteAddress(), name);
			} catch (IOException e) {
				return null;
			}
			server.userMap.put(name, message.str2);

		}
		return bb;

	}

	private ByteBuffer login(Message msg, ServerMatou server, SocketChannel sc) {
		MessageTwoString message = (MessageTwoString) msg;
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		System.out.println("Login...");
		String name = message.str1;
		String password = message.str2;
		//System.out.println("name: " + name);
		//System.out.println("pwd: " + password);
		//System.out.println(server.userMap.get(name));
		if (server.userMap.containsKey(name)) { // Username exists
			if (server.userMap.get(name).equals(password)) {
				try {
					if (server.map.containsValue(name)) {
						System.out.println(" *******  ALREADY LOGGED ********");
						bb.putInt(Opcode.LOGIN_ERR.op);
					} else {
						server.map.remove(getKey(server, name));
						server.map.put(sc.getRemoteAddress(), name);
						bb.putInt(Opcode.LOGIN_OK.op);
					}
				} catch (IOException e) {
					System.err.println("IOException, closing...");
					return null;
				}

			} else {
				bb.putInt(Opcode.LOGIN_ERR.op);
			}
		} else {
			bb.putInt(Opcode.LOGIN_ERR.op);
		}
		return bb;
	}

	private ByteBuffer message(Message msg, ServerMatou server, SocketChannel sc) {
		MessageOneString message = (MessageOneString) msg;
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String name = "temp";
		//TODO
		//get name
		if (server != null) {
			try {
				name = server.map.get(sc.getRemoteAddress());
			} catch (IOException e) {
				return null;
			}
		}
		name = "username: " + name + "\r\n";
		ByteBuffer headerToSend = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
		bb.putInt(Opcode.MESSAGEBROADCAST.op);
		ByteBuffer bodyToSend = ByteBuffer.allocate(BUFFER_SIZE);
		System.out.println("Sending : " + name +" : " + message.str);
		// add content to body's buffer
		bodyToSend.put(UTF8.encode(name));
		bodyToSend.put(UTF8.encode("data: " + message.str));
		bodyToSend.flip();

		// Add content to header's buffer
		headerToSend.put(message.endFlag);
		headerToSend.putInt(bodyToSend.remaining());
		headerToSend.flip();

		// Add header and body to ByteBuffer's response
		bb.putInt(headerToSend.limit());
		bb.put(headerToSend);
		bb.put(bodyToSend);

		return bb;
	}
	private ByteBuffer createMessage(Opcode opcode, byte flag, ByteBuffer body){
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putInt(opcode.op);
		headerToSend.clear();
		headerToSend.put(flag);
		headerToSend.putInt(body.remaining());
		headerToSend.flip();

		// Add header and body to ByteBuffer's response
		bb.putInt(headerToSend.limit());
		bb.put(headerToSend);
		bb.put(body);
		
		
		return bb;
	}
	private ByteBuffer requestPrivate(Message msg, ServerMatou server, SocketChannel sc) {
		ByteBuffer bb;
		MessageOneString message = (MessageOneString) msg;
		String name = message.str;
		if (name == null) {
			return null;
		} else {
			SocketAddress ip = getKey(server, name);
			// Prepare request
			if (ip == null) {
				opcodeAction = (Opcode.WHISP_ERR);
				ByteBuffer bodyToSend = UTF8.encode("userReq: " + name + "\r\n");
				
				bb = createMessage(Opcode.WHISP_ERR, (byte)1, bodyToSend);
			} else {
				
				opcodeAction = (Opcode.WHISP_REQUEST);
				try {
					String requester = server.map.get(sc.getRemoteAddress());
					ByteBuffer bodyToSend = UTF8.encode("username: " + requester + "\r\n");
					bb = createMessage(Opcode.WHISP_REQUEST,(byte)1, bodyToSend);
					System.out.println("\t\t<=============================>");
					System.out.println("This user: " + requester + " wants to talk to " + name);
					Set<String> pendingForUser = server.pendingConnection.get(name);
					if (pendingForUser == null) {
						pendingForUser = new HashSet<String>();
					}
					pendingForUser.add(requester);
					System.out.println("User list:");
					System.out.println(pendingForUser);
					server.pendingConnection.put(name, pendingForUser);
					System.out.println("add user to server");
					
				} catch (IOException e) {
					return null;
				}
				
			}
			
		}
		return bb;
	}

	private ByteBuffer whispOk(Message msg, ServerMatou server, SocketChannel sc) {
		MessageIp message = (MessageIp) msg;
		String username = message.username;
		String userReq = message.userReq;
		String ip = message.ip;
		System.out.println("\t\t<=============================>");
		System.out.println("This user: " + userReq + " accepted " + username);
		Set<String> validation = server.pendingConnection.get(userReq);
		if (validation != null && validation.contains(username)) {
			int port = message.port;
			int token = message.token;
			//System.out.println(msg.getBody());
			String strToSend = "username: " + username + "\r\n" 
					+ "userReq: " + userReq + "\r\n"
					+ "ip: " + ip + "\r\n" 
					+ "port: " + port + "\r\n" 
					+ "token: " + token + "\r\n";
			ByteBuffer body = UTF8.encode(strToSend);
			return createMessage(Opcode.IPRESPONSE,(byte)1 ,body );
		}
		return createMessage(Opcode.WHISP_REFUSED,(byte)1, UTF8.encode("username: " + message.userReq));
	}

	private ByteBuffer whispRefused(Message msg, ServerMatou server, SocketChannel sc) {
		MessageOneString message = (MessageOneString) msg;
		return createMessage(Opcode.WHISP_REFUSED,(byte)1, UTF8.encode("username: " + message.str));
		
	}

	/**
	 * Get the address key corresponding to the username
	 * 
	 * @param server
	 * @param username
	 * @return
	 */
	private SocketAddress getKey(ServerMatou server, String username) {
		Collection<SocketAddress> keys = server.map.keySet();
		for (SocketAddress address : keys) {
			if (server.map.get(address).equals(username)) {
				return address;
			}
		}
		return null;
	}
	
	public Opcode getOpcodeAction() {
		return opcodeAction;
	}

}
