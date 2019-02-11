# Welcome to the home of ImapCopy!

## Why use ImapCopy?

ImapCopy allow for a fast and easy synchronization of mail massages between two IMAP accounts.



## Parameters

the following command line parameters are recognized:

*  -c, --config <filename>  
Allows to specify the json configuration file, holding connection information for both the source and destination IMAP servers (file structure specified below).  

*  -v, --verbose  
Activate verbose output (default to false).  

*  -d, --maxMessageAgeDays <int>  
The max age in days of the messages to be analyzed in the source IMAP. When not specified, **all** messages are analyzed.  



## Configuration file
The configuration file tells ImapCopy how to connect to the source and destination IMAP servers. 
This file must be a single json object, with at least two properties, "source" and "destination". Other properties will be ignored.
Both "source" and "destination" must have a "connectorClass" property. Allowed values for this property are:  
*  "JavaxMailConnection"
*  "GmailApiConnection"

### JavaxMailConnection
In case "JavaxMailConnection" is specified, the connection object must have the following properties:
*  "mail.imap.host": the mail server host to connect to 
*  "mail.imap.user": username of the account on the host
*  "mail.imap.password": password for the account on the host
*  "mail.store.protocol": "imaps"  
  
Other parameters can be provided. The list of possible parameters follows the IMAP protocol. A list can be found at https://www.tutorialspoint.com/javamail_api/javamail_api_imap_servers.htm

### GmailApiConnection
In case "GmailApiConnection" is specified, the connection object must have the following properties:
*  "credentialsFilePath": path to the json credentials file, obtained from the google cloud console. The credentials must be generated with the **MAIL_GOOGLE_COM** scope.
*  "mail.imap.user": username of the account on the host






