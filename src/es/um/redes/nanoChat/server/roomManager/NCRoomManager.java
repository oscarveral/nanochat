package es.um.redes.nanoChat.server.roomManager;

import java.net.Socket;

import es.um.redes.nanoChat.server.NCServerThread;

public abstract class NCRoomManager 
{	
	protected String roomName;

	/* Método para registrar a un usuario u en una sala (se anota también su socket de comunicación). */
	public abstract boolean registerUser(String u, Socket s, NCServerThread t);
	/* Método para hacer llegar un mensaje enviado por un usuario u. */
	public abstract void broadcastMessage(String u, String message);
	/* Método para eliminar un usuario de una sala. */
	public abstract void removeUser(String u);
	/* Método para nombrar una sala. */
	public abstract void setRoomName(String roomName);
	/* Método para devolver la descripción del estado actual de la sala. */
	public abstract NCRoomDescription getDescription();
	/* Método para devolver el número de usuarios conectados a una sala. */
	public abstract int usersInRoom();
	/* Método para notificar a usuarios un cambio de nombre de la sala y actualizar el nombre de la sala en todos los hilos del servidor de los usuarios en la sala. */
	public abstract void notifyRoomNameUpdate();
	
	/* Método para obtener el nombre de la sala. */
	public String getRoomName()
	{
		return this.roomName;
	}
}