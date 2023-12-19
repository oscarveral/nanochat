package es.um.redes.nanoChat.messageFV;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
	InfoMessage
	----
	
	operation:<operation>
	name:<name>
	time:<time>
	list:<item1>,<item2>,<item3>,...
	
	Defined operations:
	- RoomInfoOk
*/

public class NCInfoMessage extends NCMessage
{
	/* Campos específicos de este tipo de mensaje. */
	private String name;
	private long time;
	private List<String> list;
	static protected final String NAME_FIELD = "name";
	static protected final String TIME_FIELD = "time";
	static protected final String LIST_FIELD = "list";

	/* Almacenamos los códigos de todos los mensajes que usan este formato. */
	public static final Set<Byte> _op_info_messages = Set.of
	(
		OP_ROOM_INFO_OK
	);
	
	/* Creamos un mensaje de tipo InfoMessage a partir del código de operación, un tiempo dado y de una lista de cadenas de texto. */
	public NCInfoMessage(byte operation, String name, long time, List<String> list) 
	{
		this.opcode = operation;
		this.name = name;
		this.time = time;
		/* Hago copia para evitar posibles errores de modificación de los datos de forma imprevista. */
		this.list = new LinkedList<String>(list);
	}

	/* Pasamos los campos del mensaje a la codificación correcta en field:value. */
	@Override
	public String toEncodedString() 
	{
		StringBuffer sb = new StringBuffer();			
		sb.append(OPCODE_FIELD+DELIMITER+opcodeToOperation(this.opcode)+END_LINE);
		sb.append(NAME_FIELD + DELIMITER + this.name + END_LINE);
		sb.append(TIME_FIELD + DELIMITER + this.time + END_LINE);
		sb.append(LIST_FIELD+DELIMITER);
		/* Para cada elemento de la lista lo ponemos en ella con su separador excepto el último que no lleva separador. */
		for (int i = 0; i < this.list.size() - 1; i++)
		{
			sb.append(this.list.get(i) + LIST_SEPARATOR);
		}
		/* Tratar el caso de que sea una lista vaccía. */
		if (!this.list.isEmpty()) sb.append(this.list.get(this.list.size() - 1) + END_LINE);
		else sb.append(END_LINE);
		sb.append(END_LINE);
		return sb.toString();
	}

	/* Parseamos el mensaje contenido en message con el fin de obtener los distintos campos y así poder construir el objeto mensaje. */
	public static NCInfoMessage readFromString(byte code, String message) 
	{
		String[] lines = message.split(String.valueOf(END_LINE));
		/* Para cada línea sacamos el índice y las dividimos por campo y valor. */
		int idxName = lines[1].indexOf(DELIMITER);
		int idxTime = lines[2].indexOf(DELIMITER);
		int idxList = lines[3].indexOf(DELIMITER);
		
		String fieldName = lines[1].substring(0, idxName).toLowerCase();                                                                                                                                                // minúsculas
		String valueName = lines[1].substring(idxName + 1).trim();
		
		String fieldTime = lines[2].substring(0, idxTime).toLowerCase();                                                                                                                                                // minúsculas
		String valueTime = lines[2].substring(idxTime + 1).trim();
		
		String fieldList = lines[3].substring(0, idxList).toLowerCase();
		String valueList = lines[3].substring(idxList + 1).trim();
		
		String name = null;
		/* Sacamos el valor del nombre. */
		if (fieldName.equalsIgnoreCase(NAME_FIELD)) name = valueName;
		else return null;
		
		long time = 0;
		/* Sacamos el tiempo del mensaje. */
		if (fieldTime.equalsIgnoreCase(TIME_FIELD)) time = Long.parseLong(valueTime);
		else return null;
		
		/* Sacamos los datos de la lista. */
		List<String> list = new LinkedList<String>();
		/* Aqui parseamos el restante del campo list para crear el array de cadenas. */
		if (fieldList.equalsIgnoreCase(LIST_FIELD)) 
		{
			String[] items = valueList.strip().split(LIST_SEPARATOR);
			for (String item : items)
			{
				/* No añadimos nombres vacíos. */
				if (item != "") list.add(item);
			}
		}
		else return null;
		
		/* Devolvemos el nuevo objeto InfoMessage. */
		return new NCInfoMessage(code, name, time, list);
	}

	public String getName ()
	{
		return this.name;
	}
	
	public long getTime ()
	{
		return this.time;
	}
	
	public List<String> getList() 
	{
		/* Protección de modificación. */
		return new LinkedList<String>(this.list);
	}
}
