package es.um.redes.nanoChat.messageFV;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
	ListMessage
	----
	
	operation:<operation>
	multiName:<name1>,<name2>,<name3>,...,<itemN>
	multiTime:<time1>,<time2>,<time3>,...,<timeN>
	multiList:<item11>,...,<item1X>;<item21>,...,<item2Y>;<item31>,...,<item3Z>;...;<itemN1>,...,<itemNM>
	
	
	Defined operations:
	- RoomListOk
*/

public class NCMultiInfoMessage extends NCMessage
{
	/* Campos específicos de este tipo de mensaje. */
	private String[] multiName;
	private long[] multiTime;
	private List<List<String>> multiList;
	static protected final String MULTI_NAME_FIELD = "multiName";
	static protected final String MULTI_TIME_FIELD = "multiTime";
	static protected final String MULTI_LIST_FIELD = "multiList";

	/* Almacenamos los códigos de todos los mensajes que usan este formato. */
	public static final Set<Byte> _op_list_messages = Set.of
	(
		OP_ROOM_LIST_OK
	);
	
	/* Creamos un mensaje de tipo ListMessage a partir del código de operación y de una lista de cadenas de texto. */
	public NCMultiInfoMessage(byte operation, String[] multiName, long[] multiTime, List<List<String>> multiList) throws IllegalArgumentException 
	{
		/* Tal y como se dice en nuestro protocolo, los tamaños de las listas deben ser iguales, si no es así no se puede codificar este mensaje. */
		if (multiName.length != multiTime.length || multiName.length != multiList.size()) throw new IllegalArgumentException("List sizes must be the same to make this message.");
		
		this.opcode = operation;
		/* Utilizo copias para evitar que se modificasen los datos desde el exterior. */
		this.multiName = Arrays.copyOf(multiName, multiName.length);
		this.multiTime = Arrays.copyOf(multiTime, multiTime.length);
		this.multiList = new LinkedList<List<String>>();
		for (List<String> list : multiList) this.multiList.add(new LinkedList<String>(list));
	}

	/* Pasamos los campos del mensaje a la codificación correcta en field:value. */
	@Override
	public String toEncodedString() 
	{
		StringBuffer sb = new StringBuffer();			
		sb.append(OPCODE_FIELD+DELIMITER+opcodeToOperation(this.opcode)+END_LINE);
		
		sb.append(MULTI_NAME_FIELD + DELIMITER);
		/* Ponemos todos los elementos de la lista con su separador. */
		for (int i = 0; i < this.multiName.length - 1; i++)
		{
			sb.append(this.multiName[i] + LIST_SEPARATOR);
		}
		sb.append(this.multiName[this.multiName.length - 1] + END_LINE);
		
		sb.append(MULTI_TIME_FIELD + DELIMITER);
		/* Ponemos todos los elementos de la lista con su separador. */
		for (int i = 0; i < this.multiTime.length - 1; i++)
		{
			sb.append(String.valueOf(this.multiTime[i]) + LIST_SEPARATOR);
		}
		sb.append(String.valueOf(this.multiTime[this.multiTime.length - 1] )+ END_LINE);
		
		sb.append(MULTI_LIST_FIELD+DELIMITER);
		/* Recorremos la lista y sublistas poniendo respectivos separadores. */
		for (int i = 0; i < this.multiList.size() - 1; i++)
		{
			List<String> currentSubList = this.multiList.get(i);
			for (int j = 0; j < currentSubList.size() - 1; j++)
			{
				sb.append(currentSubList.get(j) + LIST_SEPARATOR);
			}
			/* Tratamos que la lista pueda estar o no vacía. */
			if (!currentSubList.isEmpty()) sb.append(currentSubList.get(currentSubList.size() - 1) + MULTI_LIST_SEPARATOR);
			else sb.append(MULTI_LIST_SEPARATOR);
		}
		List<String> lastSubList = this.multiList.get(this.multiList.size() - 1);
		for (int j = 0; j < lastSubList.size() - 1; j++)
		{
			sb.append(lastSubList.get(j) + LIST_SEPARATOR);
		}
		/* Tratamos que la lista pueda estar o no vacía. */
		if (!lastSubList.isEmpty()) sb.append(lastSubList.get(lastSubList.size() - 1) + END_LINE);
		else sb.append(END_LINE);
		sb.append(END_LINE);
		/* Devolvemos la cadena codificada. */
		return sb.toString();
	}

	/* Parseamos el mensaje contenido en message con el fin de obtener los distintos campos y así poder construir el objeto mensaje. */
	public static NCMultiInfoMessage readFromString(byte code, String message) 
	{
		String[] lines = message.split(String.valueOf(END_LINE));
		
		int idxMultiName = lines[1].indexOf(DELIMITER);
		int idxMultiTime = lines[2].indexOf(DELIMITER);
		int idxMultiList = lines[3].indexOf(DELIMITER);
		
		String fieldMultiName = lines[1].substring(0, idxMultiName).toLowerCase();                                                                                                                                                // minúsculas
		String valueMultiName = lines[1].substring(idxMultiName + 1).trim();
		
		String fieldMultiTime = lines[2].substring(0, idxMultiTime).toLowerCase();
		String valueMultiTime = lines[2].substring(idxMultiTime + 1).trim();
		
		String fieldMultiList = lines[3].substring(0, idxMultiList).toLowerCase();
		String valueMultiList = lines[3].substring(idxMultiList + 1).trim();
		
		String[] multiName= null;
		/* Aqui parseamos el restante del campo multiName para crear el array de cadenas. */
		if (fieldMultiName.equalsIgnoreCase(MULTI_NAME_FIELD)) multiName = valueMultiName.split(LIST_SEPARATOR);
		else return null;
		
		long[] multiTime = null;
		/* Parseamos el resto del campo multiTime para crear el array de tiempos. */
		if (fieldMultiTime.equalsIgnoreCase(MULTI_TIME_FIELD))
		{
			multiTime = Arrays.stream(valueMultiTime.split(LIST_SEPARATOR)).map(String::trim).mapToLong(s -> Long.valueOf(s)).toArray();
		}
		else return null;
		
		List<List<String>> multiList = new LinkedList<List<String>>();
		/* Parseamos el resto del campo multiList para crear la lista de listas. */
		if (fieldMultiList.equalsIgnoreCase(MULTI_LIST_FIELD))
		{
			/* Uso la versión de split de dos parámetros ya que la de 1 parámetro elimina las cadenas vacías al final si las hubiese y en nuestro mensaje esto si es posible e indica que la lista es vacía. */
			String[] subLists= valueMultiList.split(MULTI_LIST_SEPARATOR, -1);
			for (String subList : subLists)
			{
				List<String> sl = new LinkedList<String>();
				String[] items = subList.split(LIST_SEPARATOR);
				for (String item : items) 
				{
					/* Si no es un campo vacío lo añado. */
					if (item != "") sl.add(item);
				}
				multiList.add(sl);
			}
		}
		else return null;
		
		/* Devolvemos el nuevo objeto ListMessage si los valores dados para construciión tienen los tamaños válidos. */
		try 
		{
			return new NCMultiInfoMessage(code, multiName, multiTime, multiList);
		} 
		catch (IllegalArgumentException e) 
		{
			return null;
		}
	}

	public String[] getMultiName() 
	{
		/* Devuelve una copia para salvaguardar la integridad de los datos originales. */
		return Arrays.copyOf(this.multiName, this.multiName.length);
	}
	
	public long[] getMultiTime()
	{
		/* Devuelve una copia para salvaguardar la integridad de los datos originales. */
		return Arrays.copyOf(this.multiTime, this.multiTime.length);
	}
	
	public List<List<String>> getMultiList()
	{
		/* Devuelve una copia para salvaguardar la integridad de los datos originales. */
		List<List<String>> l= new LinkedList<List<String>>();
		for (List<String> list : this.multiList) l.add(new LinkedList<String>(list));
		return l;
	}
}
