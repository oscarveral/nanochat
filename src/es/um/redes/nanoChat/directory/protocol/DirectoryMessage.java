package es.um.redes.nanoChat.directory.protocol;

/**
 * Esta clase almacena constantes relacionadas con la especificación del protocolo de comunicación cliente-servidor de directorio.
 * Todas las constantes usadas por cliente y servidor de la aplicación de directorio se encuentran aquí.
 * Se usa para encapsular el protocolo y facilitar cambios sin cambiar todo el código.
 */

public final class DirectoryMessage 
{
	/* Constantes para la comunicación. */
	public static final int TIMEOUT = 1000;
	public static final int MAX_TIMEOUTS = 10;
	public static final int DEFAULT_PORT = 6868;
	public static final int PACKET_MAX_SIZE = 128;
	public static final double DEFAULT_CORRUPTION_PROBABILITY = 0.0;
	
	/* OpCode de los diferentes posibles mensajes. */
	public static final byte OP_REGISTRATION_REQUEST = 0x01;
	public static final byte OP_REGISTRATION_OK = 0x02;
	public static final byte OP_SERVER_INFO_REQUEST = 0x03;
	public static final byte OP_SERVER_INFO_OK = 0x04;
	public static final byte OP_SERVER_INFO_NOT_FOUND = 0x05;

	/* Tamaños en bytes de los diferentes formatos de mensajes. */
	public static final int SIZE_CONTROL_MSG = 1;
	public static final int SIZE_ONE_PARAMETER_MSG = 5;
	public static final int SIZE_TWO_PARAMETER_MSG = 9;
	public static final int SIZE_IP_AND_PORT_MSG = 9;
	
	/* Función que comprueba que el OPCODE es de una solicitud, devuelve falso si es de una respuesta o no es válido el opCode. */
	public static boolean isRequestCode (byte opCode)
	{
		switch (opCode) 
		{
			case OP_REGISTRATION_REQUEST: return true;
			case OP_SERVER_INFO_REQUEST: return true;
			
			default: return false;
		}
	}
}
