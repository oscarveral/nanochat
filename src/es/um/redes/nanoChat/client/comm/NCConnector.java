package es.um.redes.nanoChat.client.comm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import es.um.redes.nanoChat.messageFV.NCControlMessage;
import es.um.redes.nanoChat.messageFV.NCInfoMessage;
import es.um.redes.nanoChat.messageFV.NCMessage;
import es.um.redes.nanoChat.messageFV.NCMultiInfoMessage;
import es.um.redes.nanoChat.messageFV.NCRoomMessage;
import es.um.redes.nanoChat.server.roomManager.NCRoomDescription;

/* Esta clase proporciona la funcionalidad necesaria para intercambiar mensajes entre el cliente y el servidor de NanoChat. */
public class NCConnector 
{
	private Socket socket;
	protected DataOutputStream dos;
	protected DataInputStream dis;
	
	public NCConnector(InetSocketAddress serverAddress) throws UnknownHostException, IOException 
	{
		/* Creamos el socket a partir de la dirección. */
		this.socket = new Socket(serverAddress.getAddress(), serverAddress.getPort());
		/* Se extraen los streams de entrada y salida. */
		this.dos = new DataOutputStream(this.socket.getOutputStream());
		this.dis = new DataInputStream(this.socket.getInputStream());
	}

	/* Método para registrar el nick en el servidor. Nos informa sobre si la inscripción se hizo con éxito o no. */
	public boolean registerNickname(String nick) throws IOException 
	{
		/* Creamos un mensaje de tipo RoomMessage con opcode OP_NICK en el que se inserte el nick. */
		NCRoomMessage message = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_REGISTER_NICK, nick);
		/* Obtenemos el mensaje de texto listo para enviar. */
		String encodedMessage = message.toEncodedString();
		/* Escribimos el mensaje en el flujo de salida, es decir, provocamos que se envíe por la conexión TCP. */
		this.dos.writeUTF(encodedMessage);
		/* Obtenemos el mensaje de respuesta. */
		NCMessage response = NCMessage.readMessageFromSocket(this.dis);
		/* Si el mnesaje obtenido es nulo devolemos ocurrió un error de comunicación. */
		if (response == null) throw new IOException();
		/* Analizamos el mensaje para saber si está duplicado el nick. */
		return response.getOpcode() == NCMessage.OP_VALID_NICK;
	}
	
	/* Método para obtener la lista de salas del servidor. Si no hay salas se devuelve una lista vacía.*/
	public List<NCRoomDescription> getRooms() throws IOException 
	{
		/* Creamos, codificamos y enviamos el mensaje. */
		NCControlMessage message = (NCControlMessage) NCMessage.makeControlMessage(NCControlMessage.OP_ROOM_LIST);
		String encodedMessage = message.toEncodedString();
		this.dos.writeUTF(encodedMessage);
		/* Recibimos la respuesta que sólo puede ser RoomListOk. */
		NCMultiInfoMessage response = (NCMultiInfoMessage) NCMessage.readMessageFromSocket(this.dis);
		/* Si la respuesta es nula ocurrió un error de comunicación. */
		if (response == null) throw new IOException();
		/* Creamos la lista de salas. */
		List<NCRoomDescription> rooms = new LinkedList<NCRoomDescription>();
		/* Sacamos los campos del mensaje. */
		String[] names = response.getMultiName();
		long[] times = response.getMultiTime();
		List<List<String>> users = response.getMultiList();
		/* Como todos los campos del mensaje tienen que tener la misma cantidad de elementos por la definición que hemos realizado del tipo de mensaje. */
		for (int i = 0; i < names.length; i++)
		{
			NCRoomDescription r = new NCRoomDescription (names[i], users.get(i), times[i]);
			rooms.add(r);
		}
		return rooms;
	}
	
	/* Método para solicitar el registro de uan nueva sala de chat. Devuelve verdadero si hubo éxito y falso en caso contrario. */
	public boolean registerRoom(String roomName) throws IOException
	{
		/* Creamos, codificamos y enviamos el mensaje. */
		NCRoomMessage message = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_CREATE_ROOM, roomName);
		String encodedMessage = message.toEncodedString();
		this.dos.writeUTF(encodedMessage);
		/* Recibimos la respuesta. */
		NCControlMessage response = (NCControlMessage) NCMessage.readMessageFromSocket(this.dis);
		/* Si la respuesta es nula hubo un error de comunicación. */
		if (response == null) throw new IOException();
		/* Dependiendo del código de operación hubo éxito o no. */
		if (response.getOpcode() == NCMessage.OP_CREATE_ROOM_OK) return true;
		return false;
	}
	
	/* Método para solicitar la entrada en una sala. */
	public boolean enterRoom(String room) throws IOException 
	{
		/* Creamos, codificamos y enviamos el mensaje. */
		NCRoomMessage message = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_ROOM_ENTER, room);
		String encodedMessage = message.toEncodedString();
		this.dos.writeUTF(encodedMessage);
		/* Recibimos la respuesta que será un mensaje de control. */
		NCControlMessage response = (NCControlMessage) NCMessage.readMessageFromSocket(this.dis);
		/* Si la respuesta es nula hubo un error de comunicación. */
		if (response == null) throw new IOException();
		/* Si hubo exito devolvemos verdadero. */
		return response.getOpcode() == NCMessage.OP_ROOM_ENTER_OK;		
	}
	
	/* Método para salir de la sala actual. Si no se estaba en sala será ignorado por el servidor. */
	public void leaveRoom() throws IOException 
	{
		/* Creamos, codificamos y enviamos el mensaje. */
		NCControlMessage message = (NCControlMessage) NCMessage.makeControlMessage(NCMessage.OP_EXIT_ROOM);
		String encodedMessage = message.toEncodedString();
		this.dos.writeUTF(encodedMessage);
	}
	
	/* Método que utiliza el Shell para ver si hay datos en el flujo de entrada. */
	public boolean isDataAvailable() throws IOException 
	{
		return (this.dis.available() != 0);
	}
	
	/* Método para enviar mensajes de texto al chat cuando nos encontramos en una sala. */
	public void sendMessage (String message) throws IOException 
	{
		/* Creamos, codificamos y enviamos el mensaje. */
		NCRoomMessage msg = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_SEND_TEXT, message);
		String encodedMessage = msg.toEncodedString();
		this.dos.writeUTF(encodedMessage);
	}
	
	/* Devuelve el mensaje recibido que después será necesario tratar según su contenido. Nunca devolera una respuesta nula.*/
	public NCMessage recieveMessage () throws IOException 
	{
		/* Leemos la respuesta. */
		NCMessage response = NCMessage.readMessageFromSocket(this.dis);
		/* Si fue nula lanzamos una excepción para indicarlo. */
		if (response == null) throw new IOException();
		
		return response;
	}
	
	/* Método para pedir la descripción de una sala. Si la sala dada de parámetro no es válida se devuelve null. */
	public NCRoomDescription getRoomInfo(String room) throws IOException 
	{
		/* Creamos, codificamos y enviamos el mensaje. */
		NCRoomMessage message = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_ROOM_INFO, room);
		String encodedMessage = message.toEncodedString();
		this.dos.writeUTF(encodedMessage);
		/* Recibimos la respuesta. */
		NCMessage response = NCMessage.readMessageFromSocket(this.dis);
		
		NCRoomDescription roomDesc = null;
		/* Según el contenido de la respuesta actuamos en consecuencia. */
		if (response.getOpcode() == NCMessage.OP_ROOM_INFO_OK)
		{
			NCInfoMessage r = (NCInfoMessage) response;
			roomDesc = new NCRoomDescription(r.getName(), r.getList(), r.getTime());
		}
		/* Devolvemos la descrición. */
		return roomDesc;
	}
	
	/* Método para solicitar el renombrado de la sala actual del usuario. Devuelve verdadero si fue válido o falso si no se pudo renombrar*/
	public boolean renameCurrentRoom(String newName) throws IOException
	{
		/* Creamos, codificamos y enviamos la solicitud. */
		NCRoomMessage message = (NCRoomMessage) NCMessage.makeRoomMessage(NCMessage.OP_RENAME_ROOM, newName);
		String encodedMessage = message.toEncodedString();
		this.dos.writeUTF(encodedMessage);
		/* Recibimos la respuesta. */
		NCMessage response = NCMessage.readMessageFromSocket(this.dis);
		/* Si el código es válido entonces devolvemos verdadero. */
		return response.getOpcode() == NCMessage.OP_RENAME_ROOM_OK;
	}
	
	/* Método para cerrar la comunicación con la sala. */
	public void disconnect() 
	{
		try 
		{
			if (this.socket != null) this.socket.close();
		} 
		catch (IOException e) {} 
		finally 
		{
			this.socket = null;
		}
	}

}
