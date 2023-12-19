package es.um.redes.nanoChat.messageFV;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class NCMessage 
{
	protected byte opcode;

	public static final byte OP_INVALID_CODE = 0;
	public static final byte OP_REGISTER_NICK = 1;
	public static final byte OP_DUPLICATED_NICK = 2;
	public static final byte OP_VALID_NICK = 3;
	public static final byte OP_ROOM_LIST = 4;
	public static final byte OP_ROOM_LIST_OK = 5;
	public static final byte OP_ROOM_ENTER = 6;
	public static final byte OP_INVALID_ROOM = 7;
	public static final byte OP_ROOM_ENTER_OK = 8;
	public static final byte OP_ROOM_INFO = 9;
	public static final byte OP_ROOM_INFO_OK = 10;
	public static final byte OP_SEND_TEXT = 11;
	public static final byte OP_RECEIVE_TEXT = 12;
	public static final byte OP_EXIT_ROOM = 13;
	public static final byte OP_CREATE_ROOM = 14;
	public static final byte OP_CREATE_ROOM_OK = 15;
	public static final byte OP_RENAME_ROOM = 16;
	public static final byte OP_RENAME_ROOM_OK = 17;
	public static final byte OP_USER_IN = 18;
	public static final byte OP_USER_OUT = 19;

	/* Constantes con los delimitadores de los mensajes de field:value. */
	public static final char DELIMITER = ':';    
	public static final char END_LINE = '\n';
	public static final String LIST_SEPARATOR = ",";
	public static final String MULTI_LIST_SEPARATOR = ";";
	
	public static final String OPCODE_FIELD = "operation";

	/* Códigos de los opcodes válidos  El orden es importante para relacionarlos con la cadena que aparece en los mensajes. */
	private static final Byte[] _valid_opcodes = 
	{
		OP_REGISTER_NICK,
		OP_DUPLICATED_NICK,
		OP_VALID_NICK,
		OP_ROOM_LIST,
		OP_ROOM_LIST_OK,
		OP_ROOM_ENTER,
		OP_INVALID_ROOM,
		OP_ROOM_ENTER_OK,
		OP_ROOM_INFO,
		OP_ROOM_INFO_OK,
		OP_SEND_TEXT,
		OP_RECEIVE_TEXT,
		OP_EXIT_ROOM,
		OP_CREATE_ROOM,
		OP_CREATE_ROOM_OK,
		OP_RENAME_ROOM,
		OP_RENAME_ROOM_OK,
		OP_USER_IN,
		OP_USER_OUT
	};

	/* Cadena exacta de cada orden. */
	private static final String[] _valid_operations_str = 
	{
		"RegisterNick",
		"DuplicatedNick",
		"ValidNick",
		"RoomList",
		"RoomListOk",
		"RoomEnter",
		"InvalidRoom",
		"RoomEnterOk",
		"RoomInfo",
		"RoomInfoOk",
		"SendText",
		"ReceiveText",
		"ExitRoom",
		"CreateRoom",
		"CreateRoomOk",
		"RenameRoom",
		"RenameRoomOk",
		"UserIn",
		"UserOut"
	};

	private static Map<String, Byte> _operation_to_opcode;
	private static Map<Byte, String> _opcode_to_operation;
	
	static 
	{
		_operation_to_opcode = new TreeMap<>();
		_opcode_to_operation = new TreeMap<>();
		for (int i = 0 ; i < _valid_operations_str.length; ++i)
		{
			_operation_to_opcode.put(_valid_operations_str[i].toLowerCase(), _valid_opcodes[i]);
			_opcode_to_operation.put(_valid_opcodes[i], _valid_operations_str[i]);
		}
	}
	
	/* Transforma una cadena en el opcode correspondiente. */
	protected static byte operationToOpcode(String opStr) 
	{
		return _operation_to_opcode.getOrDefault(opStr.toLowerCase(), OP_INVALID_CODE);
	}

	/* Transforma un opcode en la cadena correspondiente. */
	protected static String opcodeToOperation(byte opcode) 
	{
		return _opcode_to_operation.getOrDefault(opcode, null);
	}

	/* Devuelve el opcode del mensaje. */
	public byte getOpcode() 
	{
		return this.opcode;
	}

	/* Método que debe ser implementado específicamente por cada subclase de NCMessage. */
	protected abstract String toEncodedString();

	/* Extrae la operación del mensaje entrante y usa la subclase para parsear el resto del mensaje. Se devuelve null en caso de que el mensaje recibido sea no sea identificable o tenga mal alguno de sus campos.  */
	public static NCMessage readMessageFromSocket(DataInputStream dis) throws IOException 
	{
		String message = dis.readUTF();
		String[] lines = message.split(String.valueOf(END_LINE));
		if (!lines[0].isEmpty()) 
		{ 
			/* Si la línea no está vacía. */
			int idx = lines[0].indexOf(DELIMITER);
			String field = lines[0].substring(0, idx).toLowerCase();
			String value = lines[0].substring(idx + 1).trim();
			if (!field.equalsIgnoreCase(OPCODE_FIELD)) return null;
			byte code = operationToOpcode(value);
			if (code == OP_INVALID_CODE) return null;
			/* Para cada tipo de mensaje tratamos el resto del mensaje. */
			if (NCRoomMessage._op_room_messages.contains(code))
			{
				return NCRoomMessage.readFromString(code, message);
			}
			else if (NCControlMessage._op_control_messages.contains(code))
			{
				return new NCControlMessage(code);
			}
			else if (NCMultiInfoMessage._op_list_messages.contains(code))
			{
				return NCMultiInfoMessage.readFromString(code, message);
			}
			else if (NCInfoMessage._op_info_messages.contains(code))
			{
				return NCInfoMessage.readFromString(code, message);
			}
			else if (NCUserMessage._op_user_messages.contains(code))
			{
				return NCUserMessage.readFromString(code, message);
			}
			else
			{
				System.err.println("Unknown message type received:" + code);
				return null;
			}
		} else return null;
	}

	/* Método para construir un mensaje de tipo RoomMessage a partir del opcode y del nombre. */
	public static NCMessage makeRoomMessage(byte opCode, String name) 
	{
		return new NCRoomMessage(opCode, name);
	}
	
	/* Método para construir un mensaje de tipo ControlMessage a partir del opcode. */
	public static NCMessage makeControlMessage(byte opCode)
	{
		return new NCControlMessage(opCode);
	}
	
	/* Método para construir un mensaje de tipo ListMessage a partir del opCode y de una lista de cadenas de texto. ATENCIÓN Devuelve null si los tamaños de las listas y arrays no son iguales ya que el formato lo requiere según hemos especificado. */
	public static NCMessage makeMultiInfoMessage (byte opCode, String[] multiName, long[] multiTime, List<List<String>> multiList)
	{
		return new NCMultiInfoMessage(opCode, multiName, multiTime, multiList);
	}
	
	/* Método para construir un mensaje de tipo InfoMessage a partir del opCode, nombre, tiempo y lista de cadenas de texto. */
	public static NCMessage makeInfoMessage (byte opCode, String name, long time, List<String> list)
	{
		return new NCInfoMessage(opCode, name, time, list);
	}
	
	/* Método para constuir un mensaje de tipo UserMessage a partir del opCode, nombre y una cadena de texto. */
	public static NCMessage makeUserMessage (byte opCode, String name, String text)
	{
		return new NCUserMessage(opCode, name, text);
	}
}
