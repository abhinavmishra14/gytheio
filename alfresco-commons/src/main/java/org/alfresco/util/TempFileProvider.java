/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A helper class that provides temporary files, providing a common point to clean
 * them up.
 * 
 * <p>
 * The contents of ALFRESCO_TEMP_FILE_DIR [%java.io.tmpdir%/Alfresco] are managed by this 
 * class.  Temporary files and directories are cleaned by TempFileCleanerJob so that 
 * after a delay [default 1 hour] the contents of the alfresco temp dir, 
 * both files and directories are removed.
 * 
 * <p>
 * Some temporary files may need to live longer than 1 hour.   The temp file provider allows special sub folders which 
 * are cleaned less frequently.    By default, files in the long life folders will remain for 24 hours 
 * unless cleaned by the application code earlier.
 * 
 * <p>
 * The other contents of %java.io.tmpdir% are not touched by the cleaner job.
 * 
 * <p>TempFileCleanerJob Job Data: protectHours, number of hours to keep temporary files, default 1 hour.
 *  
 * @author derekh
 * @author mrogers
 */
public class TempFileProvider
{
    private static final int BUFFER_SIZE = 40 * 1024;

    /** 
     * subdirectory in the temp directory where Alfresco temporary files will go 
     */
    public static final String ALFRESCO_TEMP_FILE_DIR = "Alfresco";
    
    /**
     * The prefix for the long life temporary files.
     */
    public static final String ALFRESCO_LONG_LIFE_FILE_DIR = "longLife";

    /** the system property key giving us the location of the temp directory */
    public static final String SYSTEM_KEY_TEMP_DIR = "java.io.tmpdir";

    private static final Log logger = LogFactory.getLog(TempFileProvider.class);
    
    private static int MAX_RETRIES = 3;

    /**
     * Static class only
     */
    private TempFileProvider()
    {
    }

    /**
     * Get the Java Temp dir e.g. java.io.tempdir
     * 
     * @return Returns the system temporary directory i.e. <code>isDir == true</code>
     */
    public static File getSystemTempDir()
    {
        String systemTempDirPath = System.getProperty(SYSTEM_KEY_TEMP_DIR);
        if (systemTempDirPath == null)
        {
            throw new RuntimeException("System property not available: " + SYSTEM_KEY_TEMP_DIR);
        }
        File systemTempDir = new File(systemTempDirPath);
        if (logger.isDebugEnabled())
        {
            logger.debug("Created system temporary directory: " + systemTempDir);
        }
        return systemTempDir;
    }
    
    /**
     * Get the Alfresco temp dir, by defaut %java.io.tempdir%/Alfresco.  
     * Will create the temp dir on the fly if it does not already exist.
     * 
     * @return Returns a temporary directory, i.e. <code>isDir == true</code>
     */
    public static File getTempDir()
    {
        File systemTempDir = getSystemTempDir();
        // append the Alfresco directory
        File tempDir = new File(systemTempDir, ALFRESCO_TEMP_FILE_DIR);
        // ensure that the temp directory exists
        if (tempDir.exists())
        {
            // nothing to do
        }
        else
        {
            // not there yet
            if (!tempDir.mkdirs())
            {
                throw new RuntimeException("Failed to create temp directory: " + tempDir);
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("Created temp directory: " + tempDir);
            }
        }
        // done
        return tempDir;
    }
    
    /**
     * creates a longer living temp dir.   Files within the longer living 
     * temp dir will not be garbage collected as soon as "normal" temporary files.
     * By default long life temp files will live for for 24 hours rather than 1 hour.
     * <p>
     * Code using the longer life temporary files should be careful to clean up since 
     * abuse of this feature may result in out of memory/disk space errors.
     * @param key can be blank in which case the system will generate a folder to be used by all processes
     * or can be used to create a unique temporary folder name for a particular process.  At the end of the process 
     * the client can simply delete the entire temporary folder.  
     * @return the long life temporary directory
     */
    public static File getLongLifeTempDir(String key)
    {
        /**
         * Long life temporary directories have a prefix at the start of the 
         * folder name.
         */
        String folderName = ALFRESCO_LONG_LIFE_FILE_DIR + "_" + key;
        
        File tempDir = getTempDir();
        
        // append the Alfresco directory
        File longLifeDir = new File(tempDir, folderName);
        // ensure that the temp directory exists
        
        if (longLifeDir.exists())
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Already exists: " + longLifeDir);
            }
            // nothing to do
            return longLifeDir;
        }
        else
        {
            /**
             * We need to create a temporary directory
             * 
             * We may have a race condition here if more than one thread attempts to create 
             * the temp dir.
             *  
             * mkdirs can't be synchronized
             * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4742723
             */
            for(int retry = 0; retry < MAX_RETRIES; retry++)
            {
                boolean created = longLifeDir.mkdirs();
            
                if (created)
                {
                    // Yes we created the temp dir
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Created long life temp directory: " + longLifeDir);
                    }
                    return longLifeDir;
                }
                else
                {   
                    if(longLifeDir.exists())
                    {
                        // created by another thread, but that's O.K.  
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("Another thread created long life temp directory: " + longLifeDir);
                        }
                        return longLifeDir;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to create temp directory: " + longLifeDir);
    }

    public static File createTempFile(InputStream in, String namePrefix, String nameSufix) throws Exception
    {
        if (null == in)
        {
            return null;
        }

        File file = createTempFile(namePrefix, nameSufix);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file), BUFFER_SIZE);
        try
        {
            byte[] buffer = new byte[BUFFER_SIZE];
            int i;
            while ((i = in.read(buffer)) > -1)
            {
                out.write(buffer, 0, i);
            }
        }
        catch (Exception e)
        {
            file.delete();
            throw e;
        }
        finally
        {
            in.close();
            out.flush();
            out.close();
        }

        return file;
    }

//    /**
//     * Is this a long life folder ?
//     * @param file
//     * @return true, this is a long life folder.
//     */
//    private static boolean isLongLifeTempDir(File file)
//    {
//        if(file.isDirectory())
//        {
//            if(file.getName().startsWith(ALFRESCO_LONG_LIFE_FILE_DIR))
//            {
//                return true;
//            }
//            else
//            {
//                return false;
//            }
//        }
//        return false;
//    }

    /**
     * Create a temp file in the alfresco temp dir.
     * 
     * @return Returns a temp <code>File</code> that will be located in the
     *         <b>Alfresco</b> subdirectory of the default temp directory
     * 
     * @see #ALFRESCO_TEMP_FILE_DIR
     * @see File#createTempFile(java.lang.String, java.lang.String)
     */
    public static File createTempFile(String prefix, String suffix)
    {
        File tempDir = TempFileProvider.getTempDir();
        // we have the directory we want to use
        return createTempFile(prefix, suffix, tempDir);
    }

    /**
     * @return Returns a temp <code>File</code> that will be located in the
     *         given directory
     * 
     * @see #ALFRESCO_TEMP_FILE_DIR
     * @see File#createTempFile(java.lang.String, java.lang.String)
     */
    public static File createTempFile(String prefix, String suffix, File directory)
    {
        try
        {
            File tempFile = File.createTempFile(prefix, suffix, directory);
            if (logger.isDebugEnabled())
            {
                logger.debug("Creating tmp file: " + tempFile);
            }
            return tempFile;
        } catch (IOException e)
        {
            throw new RuntimeException("Failed to created temp file: \n" +
                    "   prefix: " + prefix + "\n"
                    + "   suffix: " + suffix + "\n" +
                    "   directory: " + directory,
                    e);
        }
    }

}
