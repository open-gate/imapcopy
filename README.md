# Welcome to the home of ImapCopy!

## Why use ImapCopy?

ImapCopy allow for a fast and easy synchronization of mail massages between two IMAP accounts.



## Parameters

Imap copy allows the following parameters:  

*  -c, --config  
The json configuration file (formatted as specified below) holding connection information for both the source and destination IMAP server.  

*  -v, --verbose  
Activate verbose output (default to false).  

*  -d, --maxMessageAgeDays  
The max age in days of the messages from to source IMAP, to be copied into the destination IMAP. When not specified, **all** messages are analyzed.  



## Configuration file
ImapCopy needs how to connect to the source and destination IMAP. This must be specified in a .json file.
The file must hold a single object, with at least two properties, "source" and "destination". Other properties will be ignored.
Both "source" and "destination" must have a "connectorClass" property. Allowed values for this property are:  
*  -"JavaxMailConnection"
*  -"GmailApiConnection"

### JavaxMailConnection
In case "JavaxMailConnection" is specified, the object must also have the following properties:  
*  -"mail.imap.host": the mail server host to connecto to 
*  -"mail.imap.user": username of the account on the host
*  -"mail.imap.password": password for the account
*  -"mail.store.protocol": "imaps"
Other parameters can be present. The list of possible parameters follows the IMAP protocol. A list can be found at https://www.tutorialspoint.com/javamail_api/javamail_api_imap_servers.htm

### GmailApiConnection
In case "GmailApiConnection" is specified, the object must also have the following properties:
*  -"credentialsFilePath": path to the json credentials file, obtained from the google cloud console. The credentials must be generated with the MAIL_GOOGLE_COM scope.
*  -"mail.imap.user": username of the account on the host






