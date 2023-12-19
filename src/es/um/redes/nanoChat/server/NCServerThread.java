package es.um.redes.nanoChat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

import javax.management.ServiceNotFoundException;

import es.um.redes.nanoChat.messageFV.NCControlMessage;
import es.um.redes.nanoChat.messageFV.NCInfoMessage;
import es.um.redes.nanoChat.messageFV.NCMessage;
import es.um.redes.nanoChat.messageFV.NCMultiInfoMessage;
import es.um.redes.nanoChat.messageFV.NCRoomMessage;
import es.um.redes.nanoChat.server.roomManager.NCRoom;
import es.um.redes.nanoChat.server.roomManager.NCRoomDescription;
import es.um.redes.nanoChat.server.roomManager.NCRoomManager;

/**
 * A new thread runs for each connected client
 */
public class NCServerThread extends Thread 
{
	private Socket socket = null;
	/* Manager global compartido entre los Threads. */
	private NCServerManager serverManager = null;
	/* Input and Output Streams. */
	private DataInputStream dis;
	private DataOutputStream dos;
	/* Usuario actual al que atiende este Thread. */
	String user;
	/* RoomManager actual (dependerá de la sala a la que entre el usuario). */
	NCRoomManager roomManager;
	/* Sala actual. */
	String currentRoom;

	/* Inicialización del hilo. */
	public NCServerThread(NCServerManager manager, Socket socket) throws IOException 
	{
		super("NCServerThread");
		this.socket = socket;
		this.serverManager = manager;
	}

	/* Main loop. */
	public void run() 
	{
		try 
		{
			/* Se obtienen los streams a partir del Socket. */
			this.dis = new DataInputStream(this.socket.getInputStream());
			this.dos = new DataOutputStream(this.socket.getOutputStream());
			/* En primer lugar hay que recibir y verificar el nick. */
			receiveAndVerifyNickname();
			/* Mientras que la conexión esté activa entonces... */
			while (true) 
			{
				NCMessage message = NCMessage.readMessageFromSocket(this.dis);
				switch (message.getOpcode()) 
				{
					/* Se nos pide la lista de salas. */
					case NCMessage.OP_ROOM_LIST:
						this.sendRoomList();
						break;
					/* Se nos pide entrar a una sala. */
					case NCMessage.OP_ROOM_ENTER:
						/* Sacamos el nombre de la sala a la que se intenta entrar. */
						NCRoomMessage msg = (NCRoomMessage) message;
						String roomName = msg.getName();
						/* Procesamos la entrada a la sala. */
						boolean joined = this.processRoomEnter(roomName);
						/* Si se entro comenzamos a procesar los mensajes de sala. */
						if (joined) this.processRoomMessages();
						break;
					/* Se nos pide información de una sala. */
					case NCMessage.OP_ROOM_INFO:
						/* Sacamos el nombre de la sala pedida. */
						NCRoomMessage infoRequest = (NCRoomMessage) message;
						String roomRequested = infoRequest.getName();
						/* Procesamos la solicitud de información. */
						this.processRoomInfo(roomRequested);
						break;
					/* Se nos pide crear una nueva sala. */
					case NCMessage.OP_CREATE_ROOM:
						/* Sacamos el nombre deseado para la nueva sala. */
						NCRoomMessage createRequest = (NCRoomMessage) message;
						String desiredName = createRequest.getName();
						/* Procesamos la creación de la sala. */
						this.processRoomCreation(desiredName);
						break;
					default:
						System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " issued a invalid request outside room.");
						break;
				}
			}
		} 
		catch (Exception e) 
		{
			/* If an error occurs with the communications the user is removed from all the managers and the connection is closed. */
			System.out.println("* User "+ this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " disconnected.");
			this.serverManager.leaveRoom(this.user, this.currentRoom);
			this.serverManager.removeUser(this.user);
		}
		finally 
		{
			System.out.println("* Closing connection with client " + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
			if (!this.socket.isClosed())
				try 
				{
					this.socket.close();
				} 
				catch (IOException e1) 
				{
					e1.printStackTrace();
				}
		}
	}
	
	/* Método para procesar la creación de una sala dado el nombre que se le quiere poner. */
	private void processRoomCreation (String name)
	{
		/* Creamos e intentamos registrar la sala. */
		System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " requested room registration with name: " + name + ".");
		NCRoomManager nuevaSala = new NCRoom();
		boolean registered = this.serverManager.registerCheckedRoomManager(nuevaSala, name);
		/* Segun el resultado del registro actuamos en consecuencia. */
		NCControlMessage response;
		String encodedResponse;
		if (registered)
		{
			System.out.println("* Sending room registration confirmation to client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
			/* Contruimos, codificamos y enviamos la respuesta. */
			response = (NCControlMessage) NCMessage.makeControlMessage(NCMessage.OP_CREATE_ROOM_OK);
			encodedResponse = response.toEncodedString();
		}
		else
		{
			System.out.println("* Sending room name already in use message to client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
			/* Contruimos, codificamos y enviamos la respuesta. */
			response = (NCControlMessage) NCMessage.makeControlMessage(NCMessage.OP_INVALID_ROOM);
			encodedResponse = response.toEncodedString();
		}
		/* Mandamos la respuesta construida. */
		try 
		{
			this.dos.writeUTF(encodedResponse);
		} 
		catch (IOException e) 
		{
			System.out.println("* Unable to send creation response to client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
		}
	}
	
	/* Método para procesar el envio de la información de una sala pedida. */
	private void processRoomInfo (String roomName)
	{
		System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " requested info of room " + roomName + ".");
		/* Obtenemos la descripción de la sala pedida. */
		NCRoomDescription roomDesc = this.serverManager.getRoomInfo(roomName);
		
		String encodedRes = null;
		if (roomDesc != null)
		{
			/* Creamos y codificamos el mensaje. */
			NCInfoMessage res = (NCInfoMessage) NCMessage.makeInfoMessage(NCMessage.OP_ROOM_INFO_OK, roomDesc.roomName, roomDesc.timeLastMessage, roomDesc.members);
			encodedRes = res.toEncodedString();
			System.out.println("* Sending room " + roomName + " information to client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
		}
		/* Si es nula entonces la sala no existe. */
		else
		{
			/* Creamos y codificamos el mensaje. */
			NCControlMessage res = (NCControlMessage) NCMessage.makeControlMessage(NCMessage.OP_INVALID_ROOM);
			encodedRes = res.toEncodedString();
			System.out.println("* Sending invalid room message to client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
		}
		
		try 
		{
			/* Mandamos la respuesta. */
			this.dos.writeUTF(encodedRes);
		} 
		catch (IOException e) 
		{
			System.out.println("* Unable to send room " + roomName + " information to client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
		}
	}
	
	/* Método para procesar la entrada a una sala. Devuelve verdadero si se entró a la sala pedida.*/
	private boolean  processRoomEnter (String room)
	{
		System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " requested to join the room " + room + ".");
		/* Intentamos meter al cliente en la sala de chat pedida. */
		try 
		{
			NCRoomManager salaPedida = this.serverManager.enterRoom(this.user, room, this.socket, this);
			/* Si se nos devuelve una sala es porque hemos entrado con éxito. */
			if (salaPedida != null)
			{
				this.currentRoom = salaPedida.getRoomName();
				this.roomManager = salaPedida;
				/* Creamos, codificamos y mandamos el mensaje de entrada a sala. */
				NCControlMessage response = (NCControlMessage) NCMessage.makeControlMessage(NCMessage.OP_ROOM_ENTER_OK);
				String encodedResponse = response.toEncodedString();
				try 
				{
					this.dos.writeUTF(encodedResponse);
					System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " joined the requested room " + room + ".");
				} 
				catch (IOException e) 
				{
					System.out.println("* Failed to send join confirmation to room " + room + " to client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
					/* Reestablecemos los parámetros como si no hubiese entrado. */
					this.serverManager.leaveRoom(this.user, this.currentRoom);
					this.currentRoom = null;
					this.roomManager = null;
				}
				/* Se tuvo exito en entrar a la sala. */
				return true;
			}
			/* Si la sala es nula no tenemos permitido entrar. Sirve para implementar baneos en salas.*/
			else
			{
				System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " is prohibited to enter room " + room + ".");
				return false;
			}
		} 
		catch (ServiceNotFoundException e) 
		{
			/* Llegar aqui implica que la sala pedida no existe. */
			System.out.println("* The room that client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " requested don't exist. Sending invalid room response.");
			/* Creamos, codificamos y mandamos el mensaje. */
			NCControlMessage response = (NCControlMessage) NCMessage.makeControlMessage(NCMessage.OP_INVALID_ROOM);
			String encodedResponse = response.toEncodedString();
			try 
			{
				this.dos.writeUTF(encodedResponse);
			} 
			catch (IOException e1) 
			{
				System.out.println("* Error sending client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " invalid room message. ");
			}
			/* Devolvemos que no hubo exito en entrar a la sala. */
			return false;
		}
	}

	/* Obtenemos el nick y solicitamos al ServerManager que verifique si está duplicado. */
	private void receiveAndVerifyNickname() throws IOException
	{
		boolean registered = false;
		while (!registered)
		{
			NCMessage registration = NCMessage.readMessageFromSocket(this.dis);
			/* Analizamos el opCode. */
			if (registration.getOpcode() == NCMessage.OP_REGISTER_NICK)
			{
				/* Recuperamos el nombre deseado. */
				NCRoomMessage reg = (NCRoomMessage) registration;
				String desiredNick = reg.getName();
				/* Validamos el nick. */
				registered = this.serverManager.addUser(desiredNick);
				/* Comunicamos el resultado. Un opcode no válido dará una respuesta de Nick duplicado por defecto.*/
				byte opCode;
				if (registered) 
				{
					opCode = NCMessage.OP_VALID_NICK;
					this.user = desiredNick;
					System.out.println("* Client " + this.socket.getInetAddress() + "/" + this.socket.getPort() + " sucessfully registered itself with nick: " + this.user + ".");
				}
				else 
				{
					opCode = NCMessage.OP_DUPLICATED_NICK;
					System.out.println("* Client " + this.socket.getInetAddress() + "/" + this.socket.getPort() + " tried to register with duplicated nickname: " + desiredNick + ".");
				}
				/* Construimos, codificamos y enviamos el mensaje. */
				NCControlMessage validNickMsg = (NCControlMessage) NCRoomMessage.makeControlMessage(opCode);
				String encodedMsg = validNickMsg.toEncodedString();
				this.dos.writeUTF(encodedMsg);
			}
			/* Si no es valido ignoramos. */
			else System.out.println("* Client " + this.socket.getInetAddress() + "/" + this.socket.getPort() + " sended bad registration request.");
		}
	}

	/* Mandamos al cliente la lista de salas existentes. */
	private void sendRoomList()
	{
		System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " requested room list.");
		
		/* Obtenemos la lista de salas. */
		List<NCRoomDescription> rooms = this.serverManager.getRoomList();
		
		/* Creamos las listas correspondientes a los diferentes campos de los mensajes. */
		List<String> roomNames = new LinkedList<String>();
		List<Long> roomTimes = new LinkedList<Long>();
		List<List<String>> roomUsers = new LinkedList<List<String>>();
	
		/* Para cada sala obtenida llenamos las listas. */
		for (NCRoomDescription room : rooms)
		{
			roomNames.add(room.roomName);
			roomTimes.add(room.timeLastMessage);
			roomUsers.add(room.members);
		}
		
		/* Creamos, codificamos y enviamos el mensaje. */
		NCMultiInfoMessage msg = (NCMultiInfoMessage) NCMessage.makeMultiInfoMessage(NCMessage.OP_ROOM_LIST_OK, roomNames.toArray(String[]::new), roomTimes.stream().mapToLong(i->i).toArray(), roomUsers);
		String encodedMsg = msg.toEncodedString();
		
		try 
		{
			this.dos.writeUTF(encodedMsg);
			System.out.println("* Sending room list to client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
		} 
		catch (IOException e) 
		{
			System.out.println("* Unable to send room list to client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
		}
	}

	private void processRoomMessages() throws IOException
	{
		boolean exit = false;
		while (!exit) 
		{
			/* Obtenemos el mensaje. */
			NCMessage message = NCMessage.readMessageFromSocket(this.dis);
			/* Según el código procedemos. */
			switch (message.getOpcode())
			{
				case NCMessage.OP_EXIT_ROOM:
					System.out.println("* Client " + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " leaved room " + this.currentRoom + ".");
					/* Como no hay que contestar a este mensaje simplemente procedemos saliendo de la sala actual. */
					this.serverManager.leaveRoom(this.user, this.currentRoom);
					this.currentRoom = null;
					this.roomManager = null;
					exit = true;
					break;
				case NCMessage.OP_ROOM_INFO:
					/* Obtenemos la sala de la que se desea obtener información. */
					NCRoomMessage mssg = (NCRoomMessage) message;
					String roomRequested = mssg.getName();
					/* Procesamos la solicitud de información. */
					this.processRoomInfo(roomRequested);
					break;
				case NCMessage.OP_ROOM_LIST:
					/* Mandamos la lista de salas. */
					this.sendRoomList();
					break;
				case NCMessage.OP_SEND_TEXT:
					/* Obtenemos el mensaje. */
					NCRoomMessage textMsg = (NCRoomMessage) message; 
					String msg = textMsg.getName();
					System.out.println("* Client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " sended message: " + msg + " on room " + this.currentRoom + ".");
					/* Retrasmitimos el mensaje. */
					this.roomManager.broadcastMessage(this.user, msg);
					break;
				/* Caso de que se quiera renombrar la sala actual. */
				case NCMessage.OP_RENAME_ROOM:
					/* Obtenemos el nuevo nombre deseado para la sala. */
					NCRoomMessage renamingMsg = (NCRoomMessage) message;
					String newName = renamingMsg.getName();
					/* Procesamos el renombrado. */
					this.proccessRenameRoom(newName);
					break;
				default:
					System.out.println("* Client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " issued bad request on room " + this.currentRoom + ".");
					break;
			}
		}
	}
	
	private void proccessRenameRoom(String name)
	{
		System.out.println("* Client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " requested to change room " + this.currentRoom + "name to " + name + ".");
		/* Intentamos renombrar la sala desde el gestor. */
		boolean renameAllowed = this.serverManager.renameRoom(this.currentRoom, name);
		/* Si no fue posible renombrar mandamos el mensaje de error al usuario. Si fue posible se encargará la propia sala de notificar a clientes y actualizar los hilos con la nueva información.*/
		if (!renameAllowed)
		{
			/* Consturimos y codificamos y enviamos la respuesta. */
			System.out.println("* Client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " tried to rename a room with a name aready in use.");
			NCControlMessage response = (NCControlMessage) NCMessage.makeControlMessage(NCMessage.OP_INVALID_ROOM);
			String encodedResponse = response.toEncodedString();
			try 
			{
				this.dos.writeUTF(encodedResponse);
			} 
			catch (IOException e) 
			{
				System.out.println("* Unable to send invalid name response to client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + ".");
			}
		}
		else
		{
			System.out.println("* Client "  + this.user + ":" + this.socket.getInetAddress() + "/" + this.socket.getPort() + " renamed his room to " + name + ". All users in room notified.");
		}
	}
	
	/* Método que actualiza el nombre de la sala actual. */
	public synchronized void setCurrentRoom (String room)
	{
		this.currentRoom = room;
	}
	public String getCurrentRoom ()
	{
		return this.currentRoom;
	}
}
