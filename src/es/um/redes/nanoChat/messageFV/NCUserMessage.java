package es.um.redes.nanoChat.messageFV;

import java.util.Set;

/**
	UserMessage
	----
	
	operation:<operation>
	name:<name>
	text:<string>
	
	Defined operations:
	- ReceiveText
*/

public class NCUserMessage extends NCMessage
{
	/* Campos específicos de este tipo de mensaje. */
	private String name;
	private String text;
	static protected final String NAME_FIELD = "name";
	static protected final String TEXT_FIELD = "text";

	/* Almacenamos los códigos de todos los mensajes que usan este formato. */
	public static final Set<Byte> _op_user_messages = Set.of
	(
		OP_RECEIVE_TEXT
	);
	
	/* Creamos un mensaje de tipo UserMessage a partir del código de operación y del nombre. */
	public NCUserMessage(byte operation, String name, String text) 
	{
		this.opcode = operation;
		this.name = name;
		this.text = text;
	}

	/* Pasamos los campos del mensaje a la codificación correcta en field:value. */
	@Override
	public String toEncodedString() 
	{
		StringBuffer sb = new StringBuffer();			
		sb.append(OPCODE_FIELD+DELIMITER+opcodeToOperation(this.opcode)+END_LINE);
		sb.append(NAME_FIELD+DELIMITER+this.name+END_LINE);
		sb.append(TEXT_FIELD + DELIMITER + this.text+END_LINE);
		sb.append(END_LINE);
		return sb.toString();
	}

	/* Parseamos el mensaje contenido en message con el fin de obtener los distintos campos y así poder construir el objeto mensaje. */
	public static NCUserMessage readFromString(byte code, String message) 
	{
		String[] lines = message.split(String.valueOf(END_LINE));
		String name = null;
		String text = null;
		
		int idxName = lines[1].indexOf(DELIMITER); 
		int idxText = lines[2].indexOf(DELIMITER);
		
		String fieldName = lines[1].substring(0, idxName).toLowerCase();                                                                                                                                                // minúsculas
		String valueName = lines[1].substring(idxName + 1).trim();
		
		String fieldText = lines[2].substring(0, idxText).toLowerCase();
		String valueText = lines[2].substring(idxText + 1).trim();
		
		if (fieldName.equalsIgnoreCase(NAME_FIELD)) name = valueName;
		else return null;
		if (fieldText.equalsIgnoreCase(TEXT_FIELD)) text = valueText;
		else return null;
		/* Devolvemos el nuevo objeto UserMessage. */
		return new NCUserMessage(code, name, text);
	}

	public String getName() 
	{
		return this.name;
	}
	
	public String getText ()
	{
		return this.text;
	}
}
