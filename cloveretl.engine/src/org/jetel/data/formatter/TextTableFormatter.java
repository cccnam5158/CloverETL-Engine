
/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2005-06  Javlin Consulting <info@javlinconsulting.cz>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/

package org.jetel.data.formatter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jetel.data.DataRecord;
import org.jetel.data.Defaults;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.util.ByteBufferUtils;

/**
 * Outputs data record in form coherent with given mask. 
 * Handles encoding of characters. Uses WriteableChannel.
 * 
 * @author avackova (agata.vackova@javlinconsulting.cz) ; 
 * (c) JavlinConsulting s.r.o.
 *  www.javlinconsulting.cz
 *
 * @since Oct 27, 2006
 *
 */
public class TextTableFormatter implements Formatter {
	
	private DataRecordMetadata metadata;
	private WritableByteChannel writer;
	private ByteBuffer fieldBuffer; 
	private ByteBuffer dataBuffer;
	private CharsetEncoder encoder;
	private String charSet = null;
	
	private List<DataRecord> dataRecords;
	private CharBuffer blank;
	private CharBuffer horizontal;
	private boolean setOutputFieldNames = true;
	private int rowSize = 0;
	private int leftBytes = 0;
	private DataFieldParams[] maskAnalize;
	private String[] mask;
	private boolean writeHeader = false;
	private boolean showCounter;
	private int counter;
	private byte[] header;
	private byte[] prefix;
	private String sCounter;
	private int counterLenght;
	private int prefixOffset; 
	private int headerOffset; 

	private static final int MAX_COUNT_ANALYZED_COUNT = 20;
	private static final int PADDING_SPACE = 3;

	private static final byte[] TABLE_CORNER = new byte[] {('+')};
	private static final char TABLE_HORIZONTAL = '-';
	private static final char TABLE_BLANK = ' ';
	private static final byte[] TABLE_VERTICAL = new byte[] {('|')};
	private static final byte[] NL = new byte[] {('\n')};
	
	/**
	 * Constructor without parameters
	 */
	public TextTableFormatter(){
		encoder = Charset.forName(Defaults.DataFormatter.DEFAULT_CHARSET_ENCODER).newEncoder();
		encoder.reset();
	}
	
	/**
	 * Constructor
	 * 
	 * @param charEncoder charset for coding characters
	 */
	public TextTableFormatter(String charEncoder){
		charSet = charEncoder;
		encoder = Charset.forName(charEncoder).newEncoder();
		encoder.reset();
	}

	/* (non-Javadoc)
	 * @see org.jetel.data.formatter.Formatter#init(org.jetel.metadata.DataRecordMetadata)
	 */
	public void init(DataRecordMetadata _metadata)
			throws ComponentNotReadyException {
		this.metadata = _metadata;

		// create buffered output stream writer and buffers 
		dataBuffer = ByteBuffer.allocateDirect(Defaults.DEFAULT_INTERNAL_IO_BUFFER_SIZE);
		fieldBuffer = ByteBuffer.allocateDirect(Defaults.DataFormatter.FIELD_BUFFER_LENGTH);
		//if mask is not given create default mask
		if (mask == null) {
			maskAnalize = new DataFieldParams[metadata.getNumFields()];
			for (int i=0;i<metadata.getNumFields();i++){
				maskAnalize[i] = new DataFieldParams(metadata.getField(i).getName(), i, 0);
			}
		} else {
			Map<String, Integer> map = new HashMap<String, Integer>();
			for (int i=0;i<metadata.getNumFields();i++){
				map.put(metadata.getField(i).getName(), i);
			}
			maskAnalize = new DataFieldParams[mask.length];
			for (int i=0;i<mask.length;i++){
				if (map.get(mask[i]) == null)
					throw new ComponentNotReadyException("Exception: Field '" + mask[i] + "' not found.");
				maskAnalize[i] = new DataFieldParams(mask[i], map.get(mask[i]), 0);
			}
		}
		dataRecords = new LinkedList<DataRecord>();
	}

    /* (non-Javadoc)
     * @see org.jetel.data.formatter.Formatter#setDataTarget(java.lang.Object)
     */
    public void setDataTarget(Object out) {
        close();
        writer = (WritableByteChannel) out;
    }
    
	/* (non-Javadoc)
	 * @see org.jetel.data.formatter.Formatter#close()
	 */
	public void close() {
        if (writer == null || !writer.isOpen()) {
            return;
        }
		try{
			flush();
			writer.close();
		}catch(IOException ex){
			ex.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see org.jetel.data.formatter.Formatter#write(org.jetel.data.DataRecord)
	 */
	public int writeRecord(DataRecord record) throws IOException {
        int sentBytes=0;
        int mark;
        
        sentBytes += writeString(TABLE_VERTICAL);

        if (showCounter) {
            counter++;
            sCounter = Integer.toString(counter);
            
			if (dataBuffer.remaining() < fieldBuffer.limit()){
				directFlush();
			}
			//change field value to bytes
			fieldBuffer.clear();
			fieldBuffer.put(prefix);
			fieldBuffer.put(sCounter.getBytes());
			fieldBuffer.flip();
            
			blank.clear();
			blank.limit(prefixOffset - fieldBuffer.limit());
            mark=dataBuffer.position();

			//put field value to data buffer
			dataBuffer.put(fieldBuffer);
			dataBuffer.put(encoder.encode(blank));
            
            sentBytes+=dataBuffer.position()-mark;
            sentBytes += writeString(TABLE_VERTICAL);
        }
        
		//for each record field which is in mask change its name to value
		for (int i=0;i<maskAnalize.length;i++){
			if (dataBuffer.remaining() < fieldBuffer.limit()){
				directFlush();
			}
			//change field value to bytes
			fieldBuffer.clear();
			record.getField(maskAnalize[i].index).toByteBuffer(fieldBuffer, encoder);
			fieldBuffer.flip();
            
			blank.clear();
			blank.limit(maskAnalize[i].length - (new String(record.getField(maskAnalize[i].index).getValue().toString().getBytes(encoder.charset().displayName())).length())); // fieldBuffer.limit() is wrong - encoding
            mark=dataBuffer.position();

			//put field value to data buffer
			dataBuffer.put(fieldBuffer);
			dataBuffer.put(encoder.encode(blank));
            
            sentBytes+=dataBuffer.position()-mark;
            sentBytes += writeString(TABLE_VERTICAL);
		}
        sentBytes += writeString(NL);
        return sentBytes;
	}

	public int writeHeader() throws IOException {
		if (!setOutputFieldNames || !writeHeader) return 0; // writeHeader depends on MAX_COUNT_ANALYZED_COUNT
		if (!isMaskAnalized()) {
			analyzeRows(dataRecords, setOutputFieldNames);
		}
        int sentBytes=0;
        sentBytes += writeString(TABLE_CORNER);
        if (showCounter) {
        	sentBytes += writeString(horizontal, counterLenght);
            sentBytes += writeString(TABLE_CORNER);
        }
        for (int i=0; i<maskAnalize.length; i++) {
        	sentBytes += writeString(horizontal, maskAnalize[i].length);
            sentBytes += writeString(TABLE_CORNER);
        }
        sentBytes += writeString(NL);
        
		DataFieldMetadata[] fMetadata = metadata.getFields();
		String fName;
        sentBytes += writeString(TABLE_VERTICAL);
        if (showCounter) {
        	sentBytes += writeString(header);
        	sentBytes += writeString(blank, headerOffset-header.length); // TODO ?
            sentBytes += writeString(TABLE_VERTICAL);
        }
        for (int i=0; i<maskAnalize.length; i++) {
        	fName = fMetadata[maskAnalize[i].index].getName();
        	sentBytes += writeString(fName.getBytes());
        	sentBytes += writeString(blank, maskAnalize[i].length-fName.length());
            sentBytes += writeString(TABLE_VERTICAL);
        }
        sentBytes += writeString(NL);
        
        sentBytes += writeString(TABLE_CORNER);
        if (showCounter) {
        	sentBytes += writeString(horizontal, counterLenght);
            sentBytes += writeString(TABLE_CORNER);
        }
        for (int i=0; i<maskAnalize.length; i++) {
        	sentBytes += writeString(horizontal, maskAnalize[i].length);
            sentBytes += writeString(TABLE_CORNER);
        }
        sentBytes += writeString(NL);

        return sentBytes;
	}
	
	public int writeFooter() throws IOException {
		if (!setOutputFieldNames) return 0;
		if (!writeHeader) {
			writeHeader = true;
			writeHeader();
			writeHeader = false;
		}
		if (!isMaskAnalized()) {
			flush();
		}
        int sentBytes=0;
        sentBytes += writeString(TABLE_CORNER);
        if (showCounter) {
        	sentBytes += writeString(horizontal, counterLenght);
            sentBytes += writeString(TABLE_CORNER);
        }
        for (int i=0; i<maskAnalize.length; i++) {
        	sentBytes += writeString(horizontal, maskAnalize[i].length);
            sentBytes += writeString(TABLE_CORNER);
        }
        sentBytes += writeString(NL);

		return sentBytes;
	}
	
	private int writeString(byte[] buffer) throws IOException {
        //int sentBytes=0;
        //int mark;
		if (dataBuffer.remaining() < buffer.length){
			directFlush();
		}
        //mark=dataBuffer.position();
        dataBuffer.put(buffer);
        //sentBytes+=dataBuffer.position()-mark;
		return new String(buffer).getBytes(encoder.charset().displayName()).length; // encoding
	}
	
	private int writeString(CharBuffer buffer, int lenght) throws IOException {
		if (lenght <= 0) return 0;
        int sentBytes=0;
        int mark;
        buffer.clear();
        buffer.limit(lenght);
		if (dataBuffer.remaining() < buffer.limit()){
			directFlush();
		}
        mark=dataBuffer.position();
		dataBuffer.put(encoder.encode(buffer));
        sentBytes+=dataBuffer.position()-mark;
		
		return sentBytes;
	}
	
	/**
	 * Writes record as 'write' function, but likewise can better format the rows.
	 * For MAX_COUNT_ANALYZED_COUNT rows return 0. Then returns count of all written rows.
	 * 
	 * @param record
	 * @throws IOException 
	 */
	public int write(DataRecord record) throws IOException {
		int size;
		if (dataRecords != null) {
			dataRecords.add(record.duplicate());
			writeHeader = true;
			if (dataRecords.size() < MAX_COUNT_ANALYZED_COUNT) {
				return 0;
			}
			analyzeRows(dataRecords, setOutputFieldNames);
			size = writeHeader();
			for (DataRecord dataRecord : dataRecords) {
				size += writeRecord(dataRecord);
			}
			dataRecords = null;
			return size;
		}
		size = writeRecord(record);
		if (leftBytes > 0) {
			size += leftBytes;
			leftBytes = 0;
		}
		return size;
	}
	
	private void analyzeRows(List<DataRecord> dataRecords, boolean header) {
		int lenght = 0;
		int max = 0;
		for (DataRecord dataRecord : dataRecords) {
			for (int i=0; i<maskAnalize.length; i++) {
				try {
					lenght = new String(dataRecord.getField(maskAnalize[i].index).getValue().toString().getBytes(encoder.charset().displayName())).length(); // encoding
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				maskAnalize[i].length = maskAnalize[i].length < lenght ? lenght : maskAnalize[i].length;
			}
		}
		if (header) {
			DataFieldMetadata[] fMetadata = metadata.getFields();
			for (int i=0; i<maskAnalize.length; i++) {
				lenght = fMetadata[maskAnalize[i].index].getName().length();
				maskAnalize[i].length = maskAnalize[i].length < lenght ? lenght : maskAnalize[i].length;
			}
		}
		for (int i=0; i<maskAnalize.length; i++) {
			maskAnalize[i].length += PADDING_SPACE;
			rowSize += maskAnalize[i].length;
		}
		rowSize++;
		
		for (DataFieldParams dataFieldParams : maskAnalize) {
			max = max > dataFieldParams.length ? max : dataFieldParams.length;
		}
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		for (int i = 0; i < max; i++) {
			sb.append(TABLE_BLANK);
			sb2.append(TABLE_HORIZONTAL);
		}
		blank = CharBuffer.wrap(sb.toString());
		horizontal = CharBuffer.wrap(sb2.toString());
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.data.formatter.Formatter#flush()
	 */
	public void flush() throws IOException {
		if (dataRecords != null) {
			analyzeRows(dataRecords, setOutputFieldNames);
			ByteBufferUtils.flush(dataBuffer,writer);
			leftBytes = writeHeader();
			for (DataRecord dataRecord : dataRecords) {
				leftBytes += writeRecord(dataRecord);
			}
			dataRecords = null;
		}
		ByteBufferUtils.flush(dataBuffer,writer);
	}
	
	private void directFlush() throws IOException {
		ByteBufferUtils.flush(dataBuffer,writer);
	}

	public int getLeftBytes() {
		return leftBytes;
	}
	
	public void setMask(String[] mask) {
		this.mask = mask;
	}
	
	/**
	 * Returns name of charset which is used by this formatter
	 * @return Name of charset or null if none was specified
	 */
	public String getCharsetName() {
		return(this.charSet);
	}

	public void setOutputFieldNames(boolean setOutputFieldNames) {
		this.setOutputFieldNames = setOutputFieldNames;
	}
	
	private boolean isMaskAnalized() {
		for (DataFieldParams params: maskAnalize) {
			if (params.length > 0) {
				return true;
			}
		}
		return false;
	}
	
	public void showCounter(String header, String prefix) throws UnsupportedEncodingException {
		this.showCounter = true;
		this.header = header.getBytes(encoder.charset().name());
		this.prefix = prefix.getBytes(encoder.charset().name());
		//int iMax = Integer.toString(Integer.MAX_VALUE).length();
		int iHeader = header.length();
		int iPrefix = prefix.length();
		counterLenght = iHeader > iPrefix + 5 ? iHeader : iPrefix + 5;
		prefixOffset = counterLenght + this.prefix.length - prefix.length();
		headerOffset = counterLenght + this.header.length - header.length();
	}
	
	/**
	 * Private class for storing data field name, its andex and lenght in mask
	 */
	class DataFieldParams {
		
		String name;
		int index;
		int length;
		
		DataFieldParams(String name,int index, int length){
			this.name = name;
			this.index = index;
			this.length = length;
		}
	}

}

