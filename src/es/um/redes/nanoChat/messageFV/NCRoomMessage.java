package es.um.redes.nanoChat.messageFV;

import java.util.Set;

/**
	RoomMessage
	----
	
	operation:<operation>
	name:<name>
	
	Defined operations:
	- RegisterNick
	- RoomEnter
	- RoomInfo
	- SendText
	- CreateRoom
	- RenameRoom
	- RenameRoomOk
	- UserIn
	- UserOut
*/

public class NCRoomMessage extends NCMessage 
{
	/* Campo específico de este tipo de mensaje. */
	private String name;
	static protected final String NAME_FIELD = "name";

	/* Almacenamos los códigos de todos los mensajes que usan este formato. */
	public static final Set<Byte> _op_room_messages = Set.of
	(
		OP_REGISTER_NICK,
		OP_ROOM_ENTER,
		OP_ROOM_INFO,
		OP_SEND_TEXT,
		OP_CREATE_ROOM,
		OP_RENAME_ROOM,
		OP_RENAME_ROOM_OK,
		OP_USER_IN,
		OP_USER_OUT
	);
	
	/* Creamos un mensaje de tipo RoomMessage a partir del código de operación y del nombre. */
	public NCRoomMessage(byte operation, String name) 
	{
		this.opcode = operation;
		this.name = name;
	}

	/* Pasamos los campos del mensaje a la codificación correcta en field:value. */
	@Override
	public String toEncodedString() 
	{
		StringBuffer sb = new StringBuffer();			
		sb.append(OPCODE_FIELD+DELIMITER+opcodeToOperation(this.opcode)+END_LINE);
		sb.append(NAME_FIELD+DELIMITER+this.name+END_LINE);
		sb.append(END_LINE);
		return sb.toString();
	}

	/* Parseamos el mensaje contenido en message con el fin de obtener los distintos campos y así poder construir el objeto mensaje. */
	public static NCRoomMessage readFromString(byte code, String message) 
	{
		String[] lines = message.split(String.valueOf(END_LINE));
		String name = null;
		
		int idx = lines[1].indexOf(DELIMITER); 
		
		String field = lines[1].substring(0, idx).toLowerCase();                                                                                                                                                // minúsculas
		String value = lines[1].substring(idx + 1).trim();
		if (field.equalsIgnoreCase(NAME_FIELD)) name = value;
		else return null;
		/* Devolvemos el nuevo objeto RoomMessage. */
		return new NCRoomMessage(code, name);
	}

	public String getName() 
	{
		return this.name;
	}
	
}
