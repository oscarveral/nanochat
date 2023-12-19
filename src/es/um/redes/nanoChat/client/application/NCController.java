package es.um.redes.nanoChat.client.application;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import es.um.redes.nanoChat.client.comm.NCConnector;
import es.um.redes.nanoChat.client.shell.NCCommands;
import es.um.redes.nanoChat.client.shell.NCShell;
import es.um.redes.nanoChat.directory.connector.DirectoryConnector;
import es.um.redes.nanoChat.messageFV.NCMessage;
import es.um.redes.nanoChat.messageFV.NCRoomMessage;
import es.um.redes.nanoChat.messageFV.NCUserMessage;
import es.um.redes.nanoChat.server.roomManager.NCRoomDescription;

public class NCController 
{
	/* Diferentes estados del cliente de acuerdo con el autómata. */
	private static final byte PRE_CONNECTION = 1;
	private static final byte PRE_REGISTER = 2;
	private static final byte OFF_ROOM = 3;
	private static final byte IN_ROOM = 4;
	/* Código de protocolo implementado por este cliente. */
	private static final int PROTOCOL = 97597757;
	/* Conector para enviar y recibir mensajes del directorio. */
	private DirectoryConnector directoryConnector;
	/* Conector para enviar y recibir mensajes con el servidor de NanoChat. */
	private NCConnector ncConnector;
	/* Shell para leer comandos de usuario de la entrada estándar. */
	private NCShell shell;
	/* Último comando proporcionado por el usuario. */
	private byte currentCommand;
	/* Nick del usuario. */
	private String nickname;
	/* Sala de chat en la que se encuentra el usuario (si está en alguna). */
	private String chatRoom;
	/* Sala de chat de la que se desea obtener información, nombre de la sala que se quiere crear o nuevo nombre deseado para una sala actual. Es la que se usa para el comando info <room> o create <room>*/
	private String auxRoom;
	/* Mensaje enviado o por enviar al chat. */
	private String chatMessage;
	/* Dirección de internet del servidor de NanoChat. */
	private InetSocketAddress serverAddress;
	/* Estado actual del cliente, de acuerdo con el autómata. */
	private byte clientStatus;

	/* Constructor. */
	public NCController() 
	{
		this.shell = new NCShell();
		this.clientStatus = PRE_CONNECTION;
		this.currentCommand = NCCommands.COM_INVALID;
		
		this.directoryConnector = null;
		this.serverAddress = null;
		this.chatMessage = null;
		this.ncConnector = null;
		this.nickname = null;
		this.chatRoom = null;
		this.auxRoom = null;
	}

	
	/* Devuelve el comando actual introducido por el usuario. */
	public byte getCurrentCommand() 
	{		
		return this.currentCommand;
	}

	/* Establece el comando actual. */
	public void setCurrentCommand(byte command) 
	{
		this.currentCommand = command;
	}

	/* Registra en atributos internos los posibles parámetros del comando tecleado por el usuario. */
	public void setCurrentCommandArguments(String[] args) 
	{
		/* Comprobaremos también si el comando es válido para el estado actual del autómata para evitar errores al cambiar atributos del cliente. */
		switch (this.currentCommand) 
		{
			case NCCommands.COM_REGISTER_NICK:
				if (this.clientStatus == PRE_REGISTER)
					this.nickname = args[0];
				break;
			case NCCommands.COM_ROOM_ENTER:
				if (this.clientStatus == OFF_ROOM)
					this.chatRoom = args[0];
				break;
			case NCCommands.COM_SEND_TEXT:
				if (this.clientStatus == IN_ROOM)
					this.chatMessage = args[0];
				break;
			/* Para la petición de información solo la podemos hacer si ya estamos registrados. Almacenamos la sala de la que deseamos obtener información. */
			case NCCommands.COM_ROOM_INFO:
				if (this.clientStatus == OFF_ROOM)
					if (args.length != 0) this.auxRoom = args[0];
					else 
					{
						this.auxRoom = null;
					}
				/* Si estamos en sala podemos aceptar info sin parámetros. */
				if (this.clientStatus == IN_ROOM)
					if (args.length != 0) this.auxRoom = args[0];
					else if (args.length == 0) this.auxRoom = this.chatRoom;
				break;
			/* para la creación de sala debemos estar registrados y fuera de sala, almacenamos el argumento.*/
			case NCCommands.COM_CREATE_ROOM:
				if (this.clientStatus == OFF_ROOM)
					this.auxRoom = args[0];
				break;
			/* Para renombrar una sala tenemos que estar dentro de la misma. */
			case NCCommands.COM_RENAME_ROOM:
				if (this.clientStatus == IN_ROOM)
					this.auxRoom = args[0];
				break;
		}
	}

	/* Procesa los comandos introducidos por un usuario que aún no está dentro de una sala. En esta función suponemos que el estado del cliente nunca será PRE_CONNECTION ni IN_ROOM */
	public void processCommand() 
	{
		switch (this.currentCommand) 
		{
			case NCCommands.COM_REGISTER_NICK:
				if (this.clientStatus == PRE_REGISTER)
					this.registerNickName();
				else
					System.out.println("* You have already registered a nickname ("+nickname+")");
				break;
			case NCCommands.COM_ROOM_LIST:
				/* En nuestro caso podemos hacer la petición de la lista de salas siempre que estemos registrados. */
				if (this.clientStatus == OFF_ROOM)
					this.getAndShowRooms();
				else
					System.out.println("* Command not allowed. You need to register to the server first.");
				break;
			case NCCommands.COM_ROOM_ENTER:
				/* Solo podemos entrar a una sala si estamos registrados. */
				if (this.clientStatus == OFF_ROOM)
					this.enterChat();
				else
					System.out.println("* Command not allowed. You need to register to the server first.");
				break;
			/* Caso específico de nuestro autómata. */
			case NCCommands.COM_ROOM_INFO:
				/* Solo podemos pedir información si estamos registrados. */
				if (this.clientStatus == OFF_ROOM)
					if (this.auxRoom != null) 
						this.getAndShowInfo();
					else 
						System.out.println("Correct use out of room: info <room>");
				else
					System.out.println("* Command not allowed. You need to register to the server first.");
				break;
			/* Caso de registro de nueva sala. */
			case NCCommands.COM_CREATE_ROOM:
				/* Solo podemos pedir un registro de sala si estamos registrados. */
				if (this.clientStatus == OFF_ROOM)
					this.registerRoom();
				else
					System.out.println("* Command not allowed. You need to register to the server first.");
				break;
			case NCCommands.COM_QUIT:
				/* Cuando salimos tenemos que cerrar todas las conexiones y sockets abiertos. El servidor eliminará al usuario de su registro. */
				this.ncConnector.disconnect();			
				this.directoryConnector.close();
				break;
			default:
		}
	}
	
	/* Método para pedir el registro de una nueva sala de chat en el servidor de chat. */
	private void registerRoom()
	{
		try 
		{
			/* Pedimos que se registre la sala. */
			boolean registered = this.ncConnector.registerRoom(this.auxRoom);
			if (registered)
			{
				/* Registro exitoso. */
				System.out.println("* Successfully created a new room called " + this.auxRoom + ".");
			}
			else
			{
				/* Registro fallido. */
				System.out.println("* Failed to create a room called " + this.auxRoom + ". There could be already a room with this name.");
			
			}
		} 
		catch (IOException e) 
		{
			System.out.println("* There was an error registering teh new room. Failed to talk to chat server.");
		}
	}
	
	/* Método para registrar el nick del usuario en el servidor de NanoChat. */
	private void registerNickName() 
	{
		try 
		{
			/* Pedimos que se registre el nick (se comprobará si está duplicado). */
			boolean registered = this.ncConnector.registerNickname(this.nickname);
			if (registered) 
			{
				/* Si el registro fue exitoso pasamos al siguiente estado del autómata. */
				this.clientStatus = OFF_ROOM;
				System.out.println("* Your nickname is now "+nickname+".");
			}
			else
				//En este caso el nick ya existía
				System.out.println("* The nickname is already registered. Try a different one.");			
		} 
		catch (IOException e) 
		{
			System.out.println("* There was an error registering the nickname. Failed to talk to chat server.");
		}
	}

	/* Método que solicita al servidor de NanoChat la lista de salas e imprime el resultado obtenido. */
	private void getAndShowRooms() 
	{
		try 
		{
			List<NCRoomDescription> salas = this.ncConnector.getRooms();
			
			/* Si la lista está vacía. */
			if (salas.isEmpty())
			{
				System.out.println("* No hay ninguna sala en el servidor.");
				return;
			}
			
			System.out.println("* Listado de salas del servidor de chat.");
			
			for (NCRoomDescription sala : salas)
			{
				System.out.println(sala.toPrintableString());
				System.out.println();
			}
			
		} 
		catch (IOException e) 
		{	
			System.out.println("* There was an error retrieving rooms data. Failed to talk to chat server.");
		}
	}

	/* Método para tramitar la solicitud de acceso del usuario a una sala concreta. */
	private void enterChat() 
	{
		try
		{
			boolean enterSucces = this.ncConnector.enterRoom(this.chatRoom);
			
			if (enterSucces)
			{
				System.out.println("* Joined chat room "+ this.chatRoom + ".");
				this.clientStatus = IN_ROOM;
				do 
				{
					/* Pasamos a aceptar sólo los comandos que son válidos dentro de una sala. */
					this.readRoomCommandFromShell();
					this.processRoomCommand();
				} 
				while (this.currentCommand != NCCommands.COM_EXIT_ROOM && this.currentCommand != NCCommands.COM_QUIT);
			}
			else
			{
				System.out.println("* Couldn't enter the room " + this.chatRoom+ ". This room don't extist.");
			}
		} 
		catch (IOException e) 
		{
			System.out.println("* There was an error joining chat room. Failed to talk to chat server.");
		}
		
		/* Llegados a este punto el usuario ha querido salir de la sala o de la aplicación, cambiamos el estado del autómata a fuera de sala. Si el comando fue salir de la aplicación como está almacenado se saldrá independientemente del estado del autómata cumpliendo con nuestro modelo. */
		this.clientStatus = OFF_ROOM;
		System.out.println("* Your are not in a room. ");
	}

	/* Método para procesar los comandos específicos de una sala. Asumimos que estamos en el estado IN_ROOM */
	private void processRoomCommand()
	{
		switch (this.currentCommand)
		{
			case NCCommands.COM_ROOM_INFO:
				/* El usuario ha solicitado información sobre la sala y llamamos al método que la obtendrá. */
				this.getAndShowInfo();
				break;
			case NCCommands.COM_ROOM_LIST:
				/* El usuario quiere la lista de salas disponibles. */
				this.getAndShowRooms();
				break;
			case NCCommands.COM_SEND_TEXT:
				/*El usuario quiere enviar un mensaje al chat de la sala. */
				this.sendChatMessage();
				break;
			case NCCommands.COM_SOCKET_IN:
				/*En este caso lo que ha sucedido es que hemos recibido un mensaje desde la sala y hay que procesarlo. */
				this.processIncommingMessage();
				break;
			case NCCommands.COM_EXIT_ROOM:
				/* El usuario quiere salir de la sala. */
				this.exitTheRoom();
				break;
			case NCCommands.COM_RENAME_ROOM:
				/* el usuario quiere renombrar la sala actual. */
				this.renameRoom();
				break;
			case NCCommands.COM_QUIT:
				/* El usuario quiere finalizar el programa. El servidor eliminará automaticamente al usuario de las salas y de su registro. */
				this.ncConnector.disconnect();
				this.directoryConnector.close();
				break;
		}
	}
	
	/* Método para procesar el renombrado de una sala. */
	private void renameRoom()
	{
		try 
		{
			boolean renamed = this.ncConnector.renameCurrentRoom(this.auxRoom);
			if (renamed)
			{
				System.out.println("* You renamed the room from " + this.chatRoom + " to " + this.auxRoom + ".");
				this.chatRoom = this.auxRoom;
			}
			else 
			{
				System.out.println("* Unable to rename current room to " + this.auxRoom + ".");
			}
		} 
		catch (IOException e) 
		{
			System.out.println("* There was an error renaming room "+this.chatRoom +".");
		}
	}
	
	/* Método para solicitar al servidor la información sobre una sala y para mostrarla por pantalla. */
	private void getAndShowInfo() 
	{
		try 
		{
			NCRoomDescription roomInfo = this.ncConnector.getRoomInfo(this.auxRoom);
			
			if (roomInfo == null) System.out.println("* The room you requested info for don't exist.");
			else
			{
				System.out.println(roomInfo.toPrintableString());
				System.out.println();
			}
		} 
		catch (IOException e) 
		{
			System.out.println("* There was an error retrieving room "+this.auxRoom +"data.");
		}
	}

	/* Método para notificar al servidor que salimos de la sala. */
	private void exitTheRoom() 
	{
		try 
		{
			this.ncConnector.leaveRoom();
		} 
		catch (IOException e) 
		{
			/* Si ocurre un error de comunicación consideraremos que ya no estamos en sala, ya que el servidor nos habrá eliminado del mismo. */
			System.out.println("* There was an error leaving the room. Failed to talk to chat server. Forcing to leave the room...");
		}
		/* El estado del autómata no lo cambiamos aquí. Lo cambiamos en la función de enterChat. De forma independiente de si se falló o no la solicitud de salida. */
	}

	/* Método para enviar un mensaje al chat de la sala. */
	private void sendChatMessage()
	{
		try 
		{
			if (this.chatMessage == "") System.out.println("* You can't send an empty message. Write something.");
			else this.ncConnector.sendMessage(this.chatMessage);
		} 
		catch (IOException e) 
		{
			System.out.println("* There was an error sending the message. Failed to talk to chat server.");
		}
	}

	/* Método para procesar los mensajes de texto recibidos del servidor mientras que el shell estaba esperando un comando de usuario en una sala. */
	private void processIncommingMessage() 
	{		
		try 
		{
			/* Recibimos el mensaje y lo tratamos según su contenido. */
			NCMessage msg = this.ncConnector.recieveMessage();
			
			/* Si el mensaje recibido es texto de una sala de chat imprimimos el mensaje. */
			if (msg.getOpcode() == NCMessage.OP_RECEIVE_TEXT)
			{
				NCUserMessage uMsg = (NCUserMessage) msg;
				System.out.println(uMsg.getName() + ": " + uMsg.getText());
			}
			/* Si el mensaje es un mensaje de renombrado. */
			else if (msg.getOpcode() == NCMessage.OP_RENAME_ROOM_OK)
			{
				NCRoomMessage renameMsg = (NCRoomMessage) msg;
				this.chatRoom = renameMsg.getName();
				System.out.println("The room you are at was renamed to " + this.chatRoom + ".");
			}
			/* Si el mensaje es información de entrada de usuario. */
			else if (msg.getOpcode() == NCMessage.OP_USER_IN)
			{
				NCRoomMessage userInMsg = (NCRoomMessage) msg;
				String user = userInMsg.getName();
				System.out.println("User " + user + " joined room "+ this.chatRoom + ".");
			}
			/* Si el mensaje es información de salida de usuario. */
			else if (msg.getOpcode() == NCMessage.OP_USER_OUT)
			{
				NCRoomMessage userOutMsg = (NCRoomMessage) msg;
				String user = userOutMsg.getName();
				System.out.println("User " + user + " left room "+ this.chatRoom + ".");
			}

		} 
		catch (IOException e) 
		{
			System.out.println("* There was an error recieving data from chat server.");
		}
	}

	/* Método para leer un comando de la sala. */
	public void readRoomCommandFromShell() 
	{
		/* Pedimos un nuevo comando de sala al shell (pasando el conector por si nos llega un mensaje entrante). */
		shell.readChatCommand(this.ncConnector);
		/* Establecemos el comando tecleado (o el mensaje recibido) como comando actual. */
		this.setCurrentCommand(shell.getCommand());
		/* Procesamos los posibles parámetros (si los hubiera). */
		this.setCurrentCommandArguments(shell.getCommandArguments());
	}

	/* Método para leer un comando general (fuera de una sala). */
	public void readGeneralCommandFromShell()
	{
		/* Pedimos el comando al shell. */
		shell.readGeneralCommand();
		/* Establecemos que el comando actual es el que ha obtenido el shell. */
		this.setCurrentCommand(shell.getCommand());
		/* Analizamos los posibles parámetros asociados al comando. */
		this.setCurrentCommandArguments(shell.getCommandArguments());
	}

	/* Método para obtener el servidor de NanoChat que nos proporcione el directorio. */
	public boolean getServerFromDirectory(String directoryHostname) 
	{
		/*Inicializamos el conector con el directorio. */
		System.out.println("* Connecting to the directory...");
		/* Intentamos obtener la dirección del servidor de NanoChat que trabaja con nuestro protocolo. */
		try 
		{
			this.directoryConnector = new DirectoryConnector(directoryHostname);
			this.serverAddress = this.directoryConnector.serverInfoRequest(PROTOCOL);
		} 
		catch (IOException e1) 
		{
			/* Nos aseguramos que la dirección se queda nula. */
			System.out.println("* Check your connection, the directory is not available.");
			this.serverAddress = null;
		}
		/* Si no hemos recibido la dirección entonces nos quedan menos intentos y no hay servidor asociado al protocolo o el servidor de direcctorio no estaba disponible. Abortamos. */
		if (this.serverAddress == null) 
		{
			return false;
		}
		else return true;
	}
	
	/* Método para establecer la conexión con el servidor de Chat (a través del NCConnector). */
	public boolean connectToChatServer() 
	{
		try 
		{
			/* Inicializamos el conector para intercambiar mensajes con el servidor de NanoChat (lo hace la clase NCConnector). */
			this.ncConnector = new NCConnector(this.serverAddress);
		} 
		catch (IOException e) 
		{
			System.out.println("* Check your connection, the chat server is not available.");
			/* Reestablecemos la dirección del servidor de chat. */
			this.serverAddress = null;
		}
		/* Si la conexión se ha establecido con éxito informamos al usuario y cambiamos el estado del autómata. */
		if (this.serverAddress != null) 
		{
			System.out.println("* Connected to chat server on: " + this.serverAddress);
			this.clientStatus = PRE_REGISTER;
			return true;
		}
		else return false;
	}

	/* Método que comprueba si el usuario ha introducido el comando para salir de la aplicación. */
	public boolean shouldQuit() 
	{
		return this.currentCommand == NCCommands.COM_QUIT;
	}

}