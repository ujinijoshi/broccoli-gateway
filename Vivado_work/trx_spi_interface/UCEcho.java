/*%
   ucecho -- Uppercase conversion example using the low speed interface of default firmware
   Copyright (C) 2009-2017 ZTEX GmbH.
   http://www.ztex.de

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
%*/

import java.io.*;
import java.util.*;
import java.text.*;
import java.nio.*;

import org.usb4java.*;

import ztex.*;

// *****************************************************************************
// ******* ParameterException **************************************************
// *****************************************************************************
// Exception the prints a help message
class ParameterException extends Exception {
    public final static String helpMsg = new String (
		"Parameters:\n"+
		"    -d <number>       Device Number (default: 0)\n" +
		"    -r                Reset EZ-USB\n" +
		"    -p                Print bus info\n" +
		"    -h                This help" );
    
    public ParameterException (String msg) {
	super( msg + "\n" + helpMsg );
    }
}


// *****************************************************************************
// ******* UCEcho **************************************************************
// *****************************************************************************
class UCEcho extends Ztex1v1 {

    // constructor
    public UCEcho ( ZtexDevice1 pDev ) throws UsbException {
	super ( pDev );
    }

    public void echo ( int addr, String input ) throws InvalidFirmwareException, UsbException, CapabilityException, IndexOutOfBoundsException {
	byte buf[] = input.getBytes();
	if (buf.length<1) return;
	int length = (buf.length+3)>>2;

	byte buf3[] = new byte[length*4];
	for (int i=0; i<buf.length; i++) buf3[i]=buf[i];

	int buf2[] = new int[length];
	for (int i=0; i<length; i++)
	    buf2[i] = (buf3[i*4+0] & 255) | ((buf3[i*4+1] & 255)<<8) | ((buf3[i*4+2] & 255)<<16) | ((buf3[i*4+3] & 255)<<24);

	System.out.println("Send " + length + " words to "+addr+" ...");
	defaultLsiSet(addr,buf2,length);

        try {
    	    Thread.sleep( 10 );
	}
	catch ( InterruptedException e) {
    	} 
	
	defaultLsiGet(addr,buf2,length);
	for (int i=0; i<length; i++) {
	    buf3[i*4+0] = (byte)(buf2[i]);
	    buf3[i*4+1] = (byte)(buf2[i]>>8);
	    buf3[i*4+2] = (byte)(buf2[i]>>16);
	    buf3[i*4+3] = (byte)(buf2[i]>>24);
	}
	System.out.println("Read "+length+" words starting from "+addr+": `"+new String(buf3,0,buf.length)+"'" );  

	if ( length>1 ) {
	    defaultLsiGet(addr+1,buf2,length-1);
	    for (int i=0; i<length; i++) {
		buf3[i*4+0] = (byte)(buf2[i]);
		buf3[i*4+1] = (byte)(buf2[i]>>8);
		buf3[i*4+2] = (byte)(buf2[i]>>16);
		buf3[i*4+3] = (byte)(buf2[i]>>24);
	    }
	    System.out.println("Read "+(length-1)+" words starting from "+(addr+1)+": `"+new String(buf3,0,buf.length-4)+"'" );  
	}
	
    }


// ******* main ****************************************************************
    public static void main (String args[]) {
    
	int devNum = 0;
	boolean reset = false;

	if ( ! System.getProperty("os.name").equalsIgnoreCase("linux") ) {
	    Runtime.getRuntime().addShutdownHook(new Thread() {
		public void run() { 
    		    Scanner s=new Scanner(System.in);
    		    System.out.println("Press <enter> to continue ...");
    		    s.nextLine();
		}
	    });	
	}

	try {
// Scan the USB. This also creates and initializes a new USB context.
	    ZtexScanBus1 bus = new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, true, false, 1);
	    
// scan the command line arguments
    	    for (int i=0; i<args.length; i++ ) {
	        if ( args[i].equals("-d") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			devNum = Integer.parseInt( args[i] );
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Device number expected after -d");
		    }
		}
		else if ( args[i].equals("-r") ) {
		    reset = true;
		}
		else if ( args[i].equals("-p") ) {
	    	    bus.printBus(System.out);
		    System.exit(0);
		}
		else if ( args[i].equals("-p") ) {
	    	    bus.printBus(System.out);
		    System.exit(0);
		}
		else if ( args[i].equals("-h") ) {
		    System.err.println(ParameterException.helpMsg);
	    	    System.exit(0);
		}
		else throw new ParameterException("Invalid Parameter: "+args[i]);
	    }

	    String errStr = "";

// create the main class	    
	    if ( bus.numberOfDevices() <= 0) {
		System.err.println("No devices found");
	        System.exit(0);
	    }
	    UCEcho ztex = new UCEcho ( bus.device(devNum) );
	    bus.unref();
	    
	    try {

// reset EZ-USB
		if ( reset ) {
		    System.out.println("Reset EZ-USB");
		    ztex.resetEzUsb();
		}
		else {
		    ztex.resetDevice(false);	// sync data toggles
		}

// check for firmware
		ztex.defaultCheckVersion(1);
		ztex.debug2PrintNextLogMessages(System.out); 
		if (ztex.config == null ) throw new Exception("Invalid configuration data");
	    
// upload the bitstream 
		String configFN = ztex.config.defaultBitstreamPath("ucecho");
		System.out.println("Found " + ztex.config.getName()+",  using bitstream "+configFN);
	        System.out.println("FPGA configuration time: " + ztex.configureFpga( configFN , true, -1 ) + " ms");

		ztex.debug2PrintNextLogMessages(System.out); 

// ucecho conversion
		String str = "";
		BufferedReader reader = new BufferedReader( new InputStreamReader( System.in ) );
		while ( ! str.equals("quit") ) {
		    System.out.print("Enter a string or `quit' to exit the program: ");
		    str = reader.readLine();
		    if ( ! str.equals("") )
			ztex.echo(10,str);
	    	    System.out.println("");
	    	}
	    	
		ztex.debug2PrintNextLogMessages(System.out); 
		ztex.dispose();  // this also releases claimed interfaces
	    	
    	    }
	    catch (Exception e) {
		System.out.println("Error: "+e.getLocalizedMessage() );
		ztex.debug2PrintNextLogMessages(System.out); 
	    } 
	}
	catch (Exception e) {
	    System.out.println("Error: "+e.getLocalizedMessage() );
	} 
	
   } 
   
}
