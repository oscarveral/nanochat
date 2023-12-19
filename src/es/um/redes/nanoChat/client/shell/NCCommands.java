package es.um.redes.nanoChat.client.shell;

import java.util.Map;
import java.util.TreeMap;

public class NCCommands 
{	
	/*Códigos para todos los comandos soportados por el shell. (Nombres modificados para adecuarse a nuestro protocolo) */
	public static final byte COM_INVALID = 0;
	public static final byte COM_ROOM_LIST = 1;
	public static final byte COM_ROOM_ENTER = 2;
	public static final byte COM_REGISTER_NICK = 3;
	public static final byte COM_SEND_TEXT = 4;
	public static final byte COM_EXIT_ROOM = 5;
	public static final byte COM_ROOM_INFO = 7;
	public static final byte COM_QUIT = 8;
	public static final byte COM_HELP = 9;
	public static final byte COM_CREATE_ROOM = 10;
	public static final byte COM_RENAME_ROOM = 11;
	public static final byte COM_SOCKET_IN = 101;
	
	/* Códigos de los comandos válidos que puede introducir el usuario del shell. El orden es importante para relacionarlos con la cadena que debe introducir el usuario y con la ayuda. */
	protected static final Byte[] _valid_user_commands = 
	{ 
		COM_ROOM_LIST, 
		COM_ROOM_ENTER,
		COM_REGISTER_NICK,
		COM_SEND_TEXT,
		COM_EXIT_ROOM, 
		COM_ROOM_INFO,
		COM_QUIT,
		COM_HELP,
		COM_CREATE_ROOM,
		COM_RENAME_ROOM
	};

	/* Cadena exacta de cada orden posible. */
	protected static final String[] _valid_user_commands_str = 
	{
		"roomlist",
		"enter",
		"nick",
		"send",
		"exit",
		"info",
		"quit",
		"help",
		"create",
		"rename"
	};

	/* Mensaje de ayuda para cada orden. */
	private static final String[] _valid_user_commands_help = 
	{
		"provides a list of available rooms to chat",
		"enter a particular <room>",
		"to set the <nickname> in the server",
		"to send a <message> in the chat",
		"to leave the current room", 
		"shows the information of the <room>",
		"to quit the application",
		"shows this information",
		"create a new room with the <name> specified",
		"changes the current room name with the a new <name>"
	};

	private static Map<String, Byte> _commands_map;
	static 
	{
		_commands_map = new TreeMap<>();
		for (int i = 0; i < _valid_user_commands_str.length; i++) 
			_commands_map.put(_valid_user_commands_str[i].toLowerCase(),_valid_user_commands[i]);
	}
	
	/* Transforma una cadena introducida en el código de comando correspondiente. */
	public static byte stringToCommand(String comStr) 
	{
		/* Busca entre los comandos si es válido y devuelve su código. Si no, COM_INVALID. */
		return _commands_map.getOrDefault(comStr.toLowerCase(), COM_INVALID);
	}

	/* Imprime la lista de comandos y la ayuda de cada uno. */
	public static void printCommandsHelp() 
	{
		System.out.println("List of commands:");
		for (int i = 0; i < _valid_user_commands_str.length; i++)
			System.out.println(_valid_user_commands_str[i] + " -- " + _valid_user_commands_help[i]);	
	}
}	

