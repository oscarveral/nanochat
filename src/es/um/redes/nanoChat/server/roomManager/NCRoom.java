package es.um.redes.nanoChat.server.roomManager;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.um.redes.nanoChat.messageFV.NCMessage;
import es.um.redes.nanoChat.messageFV.NCRoomMessage;
import es.um.redes.nanoChat.messageFV.NCUserMessage;
import es.um.redes.nanoChat.server.NCServerThread;

public class NCRoom extends NCRoomManager
{
	/* Conjunto de los usuarios en sala y mapeado de los usuarios a su socket. */
	private Map<String, Socket> usersSockets;
	/* Mapeado de cada usuario al hilo del servidor que lo gestiona. */
	private Map<String, NCServerThread> usersThreads;
	/* Tiempo en el que se envió el último mensaje. */
	private long timeLastMesage;
	
	/* Constructor de salas. */
	public NCRoom() 
	{
		this.usersSockets = new HashMap<String, Socket>();
		this.usersThreads = new HashMap<String, NCServerThread>();
		this.timeLastMesage = 0;
		/* De primeras la sala no tiene nombre, se le pondrá nombre en su registro en la lista de salas del servidor. */
		this.roomName = null;
	}
	
	@Override
	public void broadcastMessage(String u, String message) 
	{
		/* Construimos y codificamos el mensaje que vamos a mandar. */
		NCUserMessage msg = (NCUserMessage) NCMessage.makeUserMessage(NCMessage.OP_RECEIVE_TEXT, u, message);
		String encodedMsg = msg.toEncodedString();
		
		/* Iteramos sobre los sockets de todos los usuarios. */
		for (Map.Entry<String, Socket> e : this.usersSockets.entrySet())
		{
			/* Mandamos el mensaje para todos los usuarios distintos al que lo manda. */
			if (e.getKey() != u)
			{
				/* Mandamos el mensaje. */
				try 
				{
					/* Obtenemos el stream de salida. */
					DataOutputStream dos = new DataOutputStream(e.getValue().getOutputStream());
					/* Mandamos el mensaje. */
					dos.writeUTF(encodedMsg);
				} 
				catch (IOException e1) 
				{
					System.out.println("* Can't broadcast message \"" + message + "\" to client " + e.getKey() + ":" + e.getValue().getInetAddress() + "/" + e.getValue().getPort() + ".");
				}
			}
		}
		/* Establecemos el tiempo de envío del último mensaje. */
		this.timeLastMesage = System.currentTimeMillis();
	}
	
	@Override
	public NCRoomDescription getDescription() 
	{
		/* Obtenemos la lista de usuarios en sala y devolvemos una nueva descripción. */
		List<String> users = List.copyOf(usersSockets.keySet());
		return new NCRoomDescription(this.roomName, users, this.timeLastMesage);
	}
	
	@Override
	public boolean registerUser(String u, Socket s, NCServerThread t) 
	{
		/* Si el usuario ya está registrado en la sala no puede registrase. Como los nicks son únicos este caso no se debería dar. */
		if (this.usersSockets.containsKey(u)) return false;
		/* Retrasmitimos la entrada del usuario al resto de usuarios de la sala. */
		NCRoomMessage msg = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_USER_IN, u);
		String encodedMsg = msg.toEncodedString();
		for (Map.Entry<String, Socket> e : this.usersSockets.entrySet())
		{
			/* Mandamos el mensaje. */
			try 
			{
				/* Obtenemos el stream de salida. */
				DataOutputStream dos = new DataOutputStream(e.getValue().getOutputStream());
				/* Mandamos el mensaje. */
				dos.writeUTF(encodedMsg);
			} 
			catch (IOException e1) 
			{
				System.out.println("* Can't broadcast that user " + u + " joined the room to client " + e.getKey() + ":" + e.getValue().getInetAddress() + "/" + e.getValue().getPort() + ".");
			}
		}
		/* Ponemos las asociaciones en los mapas. */
		this.usersSockets.put(u, s);
		this.usersThreads.put(u, t);
		
		return true;
	}
	
	@Override
	public void removeUser(String u) 
	{
		/* Eliminamos la entrada del usuario en las tablas de asociaciones. */
		this.usersSockets.remove(u);
		this.usersThreads.remove(u);
		/* Retrasmitimos la salida del usuario al resto de usuarios de la sala. */
		NCRoomMessage msg = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_USER_OUT, u);
		String encodedMsg = msg.toEncodedString();
		for (Map.Entry<String, Socket> e : this.usersSockets.entrySet())
		{
			/* Mandamos el mensaje. */
			try 
			{
				/* Obtenemos el stream de salida. */
				DataOutputStream dos = new DataOutputStream(e.getValue().getOutputStream());
				/* Mandamos el mensaje. */
				dos.writeUTF(encodedMsg);
			} 
			catch (IOException e1) 
			{
				System.out.println("* Can't broadcast that user " + u + " left the room to client " + e.getKey() + ":" + e.getValue().getInetAddress() + "/" + e.getValue().getPort() + ".");
			}
		}
	}
	
	@Override
	public void setRoomName(String roomName) 
	{
		this.roomName = roomName;
	}
	
	@Override
	public int usersInRoom() 
	{
		return this.usersSockets.size();
	}
	
	@Override
	public void notifyRoomNameUpdate() 
	{
		/* Actualizamos todos los hilos de los usuarios. */
		this.threadRoomNameUpdate();
		/* Mandamos la actualización a los clientes de los usuarios. */
		this.clientRoomNameUpdate();
	}
	
	/* Método que se encarga de retransmitir a todos los clientes de los usuarios el nombre actual de la sala. */
	private void clientRoomNameUpdate()
	{
		/* Contruiomos y codificamos el mensaje que vamos a mandar. */
		NCRoomMessage update = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_RENAME_ROOM_OK, this.roomName);
		String encodedUpdate = update.toEncodedString();
		
		/* Iteramos sobre los sockets de todos los usuarios. */
		for (Map.Entry<String, Socket> e : this.usersSockets.entrySet())
		{	
			try 
			{
				/* Obtenemos el stream de salida. */
				DataOutputStream dos = new DataOutputStream(e.getValue().getOutputStream());
				/* Mandamos el mensaje. */
				dos.writeUTF(encodedUpdate);
			} 
			catch (IOException e1) 
			{
				System.out.println("* Can't send room name update to client" +  e.getKey() + ":" + e.getValue().getInetAddress() + "/" + e.getValue().getPort() + ".");
			}
		}
	}
	
	/* Método que se encarga de retrasmitir el nombre de sala a cada uno de los hilos de los usuarios. */
	private void threadRoomNameUpdate()
	{
		/* Recorremos el mapeado actualizando los valores. */
		for (Map.Entry<String, NCServerThread> e : this.usersThreads.entrySet())
		{
			e.getValue().setCurrentRoom(this.roomName);
		}
	}
}
