/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
 * This file is part of an Alfresco messaging investigation
 *
 * The Alfresco messaging investigation is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Alfresco messaging investigation is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the Alfresco messaging investigation. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.content.hash.javase;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;

import org.alfresco.content.hash.AbstractContentHashNodeWorker;

/**
 * A Java SE implementation of a content hash node worker
 * 
 * @author Ray Gauss II
 */
public class JavaSeContentHashNodeWorker extends AbstractContentHashNodeWorker
{
    private static final int BUFFER_SIZE = 8*1024;

    @Override
    public String generateHashInternal(InputStream source, String hashAlgorithm) throws Exception
    {
        if (source == null || hashAlgorithm == null) {
            throw new IllegalArgumentException("source and hashAlgorithm must not be null");
        }
        try
        {
            MessageDigest messageDigest = MessageDigest.getInstance(hashAlgorithm);
        
            byte[] buffer = new byte[BUFFER_SIZE];
        
            int bytesRead = 0;
            while( (bytesRead = source.read(buffer)) > 0) {
                messageDigest.update(buffer, 0, bytesRead);
            }
            
            return encodeHex(messageDigest.digest());
        }
        finally
        {
            source.close();
        }
    }
    
    /**
     * Performs a hex encoding of the given byte array
     * 
     * @param byteArray
     * @return the hex encoded value
     */
    protected String encodeHex(byte[] byteArray)
    {
        BigInteger integer = new BigInteger(1, byteArray);
        return integer.toString(16);
    }
    
}
