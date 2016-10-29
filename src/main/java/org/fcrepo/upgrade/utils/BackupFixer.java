/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.upgrade.utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.zip.GZIPInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * This utility is designed to be used to help migrate from 4.4, 4.5, and 4.6 to Fedora 4.7.0
 *
 * It should be run on the directory produced by a /fcr:backup request, prior to requesting /fcr:restore
 *
 * Warning: This utility is destructive (but likely not in a bad way), and should be run against a copy of the backup
 * if the backup is irreplaceable.
 *
 * @author escowles
 * @since 2016-10-28
 */
public class BackupFixer {

    /**
     * Private constructor
     */
    private BackupFixer() {
        // prevent public construction
    }

    /**
     * main
     * @param args from command line
     * @throws IOException
     */
    public static void main( final String[] args ) throws IOException {
        if ( args.length < 1 ) {
            System.err.println("usage: BackupFixer [backup directory]");
            return;
        }

        final File dir = new File(args[0]);

        if ( !dir.exists() || dir.listFiles().length == 0 ) {
            System.err.println("Directory " + args[0] + " is empty or doesn't exist");
            return;
        }

        final File[] files = dir.listFiles();
        for ( int i = 0; i < files.length; i++ ) {
            final String fn = files[i].getName();
            if ( files[i].isFile() && fn.startsWith("documents_") && fn.endsWith(".gz") ) {
                fix(files[i]);
            }
        }
    }
    private static void fix(final File f) throws IOException {
        System.out.println("Fixing: " + f.getName());

        // move original file
        final File moved = new File(f.getParent(), f.getName() + ".orig");
        if ( moved.exists() ) {
            System.err.println("Backup file already exists, skipping: " + moved.getName());
            return;
        }
        f.renameTo(moved);

        // filter duplicate ids
        PrintWriter out = null;
        try {
            final BufferedReader buf = new BufferedReader(new InputStreamReader(new GZIPInputStream(
                    new FileInputStream(moved))));
            out = new PrintWriter(new GZIPOutputStream(new FileOutputStream(f)));

            final HashSet<String> ids = new HashSet<>();
            for ( String line = null; (line = buf.readLine()) != null; ) {
                final String id = idFromJSON(line);
                if ( id == null ) {
                    System.err.println("Unable to parse id: " + line);
                } else if ( ids.contains(id) ) {
                    System.err.println("Skipping duplicate id: " + line);
                } else {
                    ids.add(id);
                    out.println(line);
                }
            }
        } finally {
            out.close();
        }
    }
    private static String idFromJSON(final String s) {
        try {
            final JsonParser parser = new JsonFactory().createParser(s);
            boolean metadata = false;
            while ( parser.nextToken() != JsonToken.END_OBJECT ) {
                if ( "metadata".equals(parser.getCurrentName()) ) {
                    metadata = true;
                } else if ( metadata && parser.getCurrentName().equals("id") ) {
                    parser.nextToken();
                    return parser.getText();
                }
            }
        } catch ( IOException ex ) {
            System.err.println("Exception during parsing: " + ex.toString());
        }
        return null;
    }
}