package es.um.redes.nanoChat.messageFV;

import java.util.Set;

/**
	ControlMessage
	----
	
	operation:<operation>
	
	Defined operations:
	- DuplicatedNick
	- ValidNick
	- RoomList
	- InvalidRoom
	- RoomEnterOk
	- ExitRoom
	- CreateRoomOk
*/

public class NCControlMessage extends NCMessage
{
	/* Almacenamos los códigos de todos los mensajes que usan este formato. */
	public static final Set<Byte> _op_control_messages = Set.of
	(
		OP_DUPLICATED_NICK,
		OP_VALID_NICK,
		OP_ROOM_LIST,
		OP_INVALID_ROOM,
		OP_ROOM_ENTER_OK,
		OP_EXIT_ROOM,
		OP_CREATE_ROOM_OK
	);
	
	/* Creamos un mensaje de tipo ControlMessage a partir del código de operación. */
	public NCControlMessage(byte operation) 
	{
		this.opcode = operation;
	}

	/* Pasamos el campo opcode del mensaje a la codificación correcta en field:value. */
	@Override
	public String toEncodedString() 
	{
		StringBuffer sb = new StringBuffer();			
		sb.append(OPCODE_FIELD+DELIMITER+opcodeToOperation(this.opcode)+END_LINE);
		sb.append(END_LINE);
		return sb.toString();
	}
	
	/* NOTA. Como no hay mas campos no se hace necesario parsear el mensaje con readFromStrin. Como la superclase ya identifica el código puede crear directamente el objeto mensaje llamando al constructor desde la superclase. */
	
	/* NOTA: Como este mensaje sólo contiene el código de operación y la función getOpCode ya está implementada en la superclase no hace falta aquí. */
}
