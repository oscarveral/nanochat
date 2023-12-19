package es.um.redes.nanoChat.directory.connector;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import es.um.redes.nanoChat.directory.protocol.DirectoryMessage;

/**
 * Cliente con métodos de consulta y actualización específicos del directorio
 */
public class DirectoryConnector 
{	
	/* Socket UDP y dirección del servidor de directorio. */
	private DatagramSocket socket; 
	private InetSocketAddress directoryAddress; 
	
	/* Constructor del conector con el directorio. */
	public DirectoryConnector(String agentAddress) throws IOException 
	{
		// A partir de la dirección dada de parámetro y del puerto por defecto construimos la dirección de conexión al servidor de directorio.
		this.directoryAddress = new InetSocketAddress(InetAddress.getByName(agentAddress), DirectoryMessage.DEFAULT_PORT);
		// Creación de un socket UDP
		this.socket = new DatagramSocket();
	}

	/* Envía una solicitud para obtener el servidor de chat asociado a un determinado protocolo. Si no hay servidor asociado se devuelve null, en caso contrario se devuelve la dirección encontrada. Si se falla en la solicitud tambien se devuelve null.*/
	public InetSocketAddress serverInfoRequest(int protocol) throws IOException 
	{
		/* Construimos primero el mensaje de consulta que enviaremos al directorio y el datagrama con la consulta. */
		byte[] request = buildServerInfoRequest(protocol);
		DatagramPacket pcktReq = new DatagramPacket(request, request.length, this.directoryAddress);
		
		/* Construimos el buffer y datagrama para la respuesta. */
		byte[] response = new byte[DirectoryMessage.PACKET_MAX_SIZE];
		DatagramPacket pcktRes = new DatagramPacket(response, response.length);
		
		/* Establecemos el temporizador para el caso en que no haya respuesta. */
		this.socket.setSoTimeout(DirectoryMessage.TIMEOUT);
		
		/* Contador de intentos. */
		int attempts = 0;
		/* Bucle de intentos. */
		while (attempts < DirectoryMessage.MAX_TIMEOUTS)
		{
			/*Enviamos datagrama por el socket. */
			this.socket.send(pcktReq);
	
			/* Intentamos recibir respuesta. */
			try 
			{
				while (true)
				{
					/* Recibimos el mensaje. */
					this.socket.receive(pcktRes);
					/* Intentamos decodificar la respuesta. Si no es posible es porque recibimos un paquete que no fue respuesta a nuestra solicitud, entonces seguimos esperando.*/
					try 
					{
						/* Devolvemos la dirección del servidor o null si no lo había. */
						InetSocketAddress chatServAddr = this.decodeServerInfoResponse(pcktRes);
						if (chatServAddr == null) System.out.println("* Connection succeded. No server found for protocol " + protocol + ".");
						else System.out.println("* Connection succeded. Chat server address " + chatServAddr.toString());
						return chatServAddr;
					}
					/* En caso de que no recibiesemos respuesta continuamos escuchando en busca de una. */
					catch (UnknownHostException e) {continue;}
				}
			} 
			catch (SocketTimeoutException e) 
			{
				attempts++;
			}
		}
		/* Llegar aquí implica haber fallado en recibir respuesta del directorio, por tanto devolvemos null. */
		System.out.println("* Connection failed. Can't connect to directory server.");
		return null;
	}

	/* Método para generar el mensaje de consulta (para obtener el servidor asociado a un protocolo). */
	private byte[] buildServerInfoRequest(int protocol) 
	{
		/* Buffer donde construimos el mensaje. */
		ByteBuffer data = ByteBuffer.allocate(DirectoryMessage.SIZE_ONE_PARAMETER_MSG);
		
		/* Ponemos el OpCode de solicitar la información del servidor. */
		data.put(DirectoryMessage.OP_SERVER_INFO_REQUEST);
		
		/* Ponemos a continuación el protocolo del servidor que solicitamos. */
		data.putInt(protocol);
		
		/* Devolvemos el array de bytes. */
		return data.array();
	}

	/* Método para extraer la dirección de internet a partir del mensaje UDP de respuesta a ServerInfoRequest. Si no hay servidor asociado se devuelve null. */
	private InetSocketAddress decodeServerInfoResponse(DatagramPacket packet) throws UnknownHostException 
	{
		/* Obtenemos el array de datos y construimos el buffer. */
		byte [] res = packet.getData();
		ByteBuffer responseBuffer = ByteBuffer.wrap(res);
		
		/* Obtenemos el código de operacion y analizamos su validez. */
		byte opCode = responseBuffer.get();
		
		if (opCode == DirectoryMessage.OP_SERVER_INFO_NOT_FOUND) return null;
		else if (opCode == DirectoryMessage.OP_SERVER_INFO_OK)
		{
			/* Obtenemos un array de bytes con la ip del servidor de chat. */
			byte[] rawServerIp = new byte[4];
			responseBuffer.get(rawServerIp);
			/* Obtenemos el puerto del servidor. */
			int serverPort = responseBuffer.getInt();
			
			/* Construimos la direccion del servidor. */
			InetAddress serverIp = InetAddress.getByAddress(rawServerIp);
			InetSocketAddress address = new InetSocketAddress(serverIp, serverPort);
			
			return address;
		}
		/* Si llegase un mensaje con un OpCode no válido lanzamos una excepción. */
		throw new UnknownHostException("Chat server addres could not be resolved.");
	}
	
	/* Envía una solicitud para registrarse comoservidor de chat asociado a un determinado protocolo. Devuelve true si tuvo exito y false si hubo algún problema. */
	public boolean registrationRequest(int protocol, int port) throws IOException 
	{
		/* Construimos primero el mensaje de consulta que enviaremos al directorio y el datagrama con la consulta. */
		byte[] request = buildRegistrationRequest(protocol, port);
		DatagramPacket pcktReq = new DatagramPacket(request, request.length, this.directoryAddress);
		
		/* Construimos el buffer y datagrama para la respuesta. */
		byte[] response = new byte[DirectoryMessage.PACKET_MAX_SIZE];
		DatagramPacket pcktRes = new DatagramPacket(response, response.length);
		
		/* Establecemos el temporizador para el caso en que no haya respuesta. */
		this.socket.setSoTimeout(DirectoryMessage.TIMEOUT);
		
		/* Contador de intentos. */
		int attempts= 0;
		/* Bucle de intentos de recibir respuesta */
		while (attempts < DirectoryMessage.MAX_TIMEOUTS)
		{
			/*Enviamos datagrama por el socket. */
			this.socket.send(pcktReq);
								
			/* Intentamos recibir respuesta. */
			try 
			{
				/**
				 *  Intentaremos recibir respuesta hasta que nos llegue una respuesta adecuada a nuestra solicitud o hasta que salte el timeout. 
				 *  Este bucle permite ignorar respuesta que llegasen a solicitudes no realizadas por nosotros.
				 */
				while (true)
				{
					/* Recibimos la respuesta. Si salta el timout reintentaresmos enviarla.*/
					socket.receive(pcktRes);

					/* Construimos el buffer de respuesta. */
					ByteBuffer responseBuffer = ByteBuffer.wrap(pcktRes.getData());
					
					/* Sacamos el código de operación. */
					int responseCode = responseBuffer.get();
					
					/* Comprobamos el valor de la operación. */
					if (responseCode == DirectoryMessage.OP_REGISTRATION_OK) return true;
				}
			} 
			catch (SocketTimeoutException e) 
			{
				/* Si fallamos en tener respuesta reintentamos tener respuesta. */
				attempts++;
			}
		}
		/* Si nos pasamos de intentos simplemente devolvemos false. */
		return false;
	}

	/* Método para construir una solicitud de registro de servidor. */
	private byte[] buildRegistrationRequest(int protocol, int port) 
	{
		/* Buffer donde construimos el mensaje. */
		ByteBuffer data = ByteBuffer.allocate(DirectoryMessage.SIZE_TWO_PARAMETER_MSG);
		
		/* Ponemos el OPCODE de solicitar el registro. */
		data.put(DirectoryMessage.OP_REGISTRATION_REQUEST);
		
		/* Ponemos a continuación el protocolo y el puerto al del servidor que queremos registrar. */
		data.putInt(protocol);
		data.putInt(port);
		
		/* Devolvemos el array de bytes. */
		return data.array();
	}
	
	/* Método para cerrar el socket con el que nos comunicamos con el servidor de directorio. */
	public void close() 
	{
		this.socket.close();
	}
}
