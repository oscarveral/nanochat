package es.um.redes.nanoChat.server;

import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.ServiceNotFoundException;

import es.um.redes.nanoChat.server.roomManager.NCRoomDescription;
import es.um.redes.nanoChat.server.roomManager.NCRoomManager;

/**
 * Esta clase contiene el estado general del servidor (sin la lógica relacionada con cada sala particular)
 */
class NCServerManager 
{
	/* Primera habitación del servidor. */
	final static byte INITIAL_ROOM = 'A';
	final static String ROOM_PREFIX = "Room";
	/* Siguiente habitación que se creará. */
	byte nextRoom;
	/* Usuarios registrados en el servidor. */
	private Set<String> users;
	/* Habitaciones actuales asociadas a sus correspondientes RoomManagers. */
	private Map<String,NCRoomManager> rooms;

	NCServerManager() 
	{
		this.nextRoom = INITIAL_ROOM;
		this.users = new HashSet<String>();
		this.rooms = new HashMap<String,NCRoomManager>();
	}

	/* Método para registrar un RoomManager. No realiza comprobación de nombres, pensado solo para uso interno e inicialización del servidor. */
	public void registerRoomManager(NCRoomManager rm) 
	{
		/* Registramos el roomManager. */
		String roomName = ROOM_PREFIX + (char) this.nextRoom;
		this.rooms.put(roomName, rm);
		rm.setRoomName(roomName);
	}
	/* Método que permite registrar la sala con el nombre que quieras comprobando que el nombre no esté en uso, devuelve false si estaba en uso y true en caso contrario. */
	public synchronized boolean registerCheckedRoomManager(NCRoomManager rm, String roomName)
	{
		/* Comprobamos si el nombre está en uso. */
		if (this.rooms.containsKey(roomName)) return false;
		/* Si no está en uso registramos la sala con el nombre dado. */
		this.rooms.put(roomName, rm);
		rm.setRoomName(roomName);
		/* Devolvemos true por defecto. */
		return true;
	}

	/* Devuelve la descripción de las salas existentes. Si no hay ninguna sala se devuelve una lista vacía.*/
	public synchronized List<NCRoomDescription> getRoomList() 
	{
		List<NCRoomDescription> desc = new LinkedList<NCRoomDescription>();
		/* Para cada sala registrada añadimos su descripción a la lista y la devolvemos. */
		this.rooms.forEach((String s, NCRoomManager r)-> desc.add(r.getDescription()));
		return desc;
	}

	/* Intenta registrar al usuario en el servidor. */
	public synchronized boolean addUser(String user) 
	{
		return this.users.add(user);
	}

	/* Elimina al usuario del servidor. */
	public synchronized void removeUser(String user) 
	{
		this.users.remove(user);
	}
	
	/* Método que obtiene la infomación de una sala en concreto. Devuelve null si la sala no existe.*/
	public synchronized NCRoomDescription getRoomInfo(String roomName)
	{
		/* Obtenemos la sala deseada. */
		NCRoomManager room = this.rooms.getOrDefault(roomName, null);
		/* Si es nula es porque no existe la sala, devolvemos null. */
		if (room == null) return null;
		/* Si existe devolvemos su descripción. */
		return room.getDescription();
	}

	/* Un usuario solicita acceso para entrar a una sala y registrar su conexión en ella. Si la sala pedida no existe se devuelve un roomManager se lanza una excepción. Si no está permitido que el usuario entre a la sala se devuelve null.*/
	public synchronized NCRoomManager enterRoom(String u, String room, Socket s, NCServerThread t) throws ServiceNotFoundException
	{
		NCRoomManager roomRequested = this.rooms.getOrDefault(room, null);
		/* Si la sala no existe valía null y lanzamos una excepción que indica que no existe. */
		if (roomRequested == null) throw new ServiceNotFoundException();
		
		boolean joined = roomRequested.registerUser(u, s, t);

		/* Si la sala existe y si es aceptado en la sala entonces devolvemos el RoomManager de la sala. */
		if (joined) return roomRequested; 
		/* Si no es aceptado devlvemos null. */
		return null;
	}

	/* Un usuario deja la sala en la que estaba. */
	public synchronized void leaveRoom(String u, String room) 
	{
		NCRoomManager roomRequested = this.rooms.getOrDefault(room, null);
		/* Si la sala no existe volvemos. */
		if (roomRequested == null) return;
		/* Sacamos al usuario de la sala. */
		roomRequested.removeUser(u);
		/* Si la sala quedase vacía no hacemos nada, no vamos a eliminar salas. */
	}
	
	/* Método que intentar renombrar una determinada sala del servidor. Devuelve true si se puede renombrar la sala y false en caso contrario o si la sala que se desea renombrar no existe. */
	public synchronized boolean renameRoom(String roomWanted, String newName)
	{
		/* Si ya hay una sala con este nombre no podemos renombrar. */
		if (this.rooms.containsKey(newName)) return false;
		/* Si el nombre está disponible reemplazamos la entrada en el mapa y renombramos la sala. */
		NCRoomManager renamedRoom = this.rooms.getOrDefault(roomWanted, null);
		if (renamedRoom == null) return false;
		this.rooms.remove(roomWanted);
		this.rooms.put(newName, renamedRoom);
		/* Actualizamos el nombre de la propia sala y notificamos a sus usuarios (hilos del servidor y clientes) el cambio de nombre. */
		renamedRoom.setRoomName(newName);
		renamedRoom.notifyRoomNameUpdate();
		return true;
	}
}
