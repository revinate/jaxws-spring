package com.revinate.ws.spring;

import com.sun.xml.ws.api.server.SDDocumentSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class SDDocumentCollector {

    public static Map<URL, Object> collectDocs(String dirPath, ClassLoader cl) {
        Map<URL, Object> docs = new HashMap<>();
        URL url = cl.getResource(dirPath);
        if (url != null) {
            if ("file".equals(url.getProtocol())) {
                File file;
                try {
                    file = new File(url.toURI());
                } catch (URISyntaxException e) {
                    file = new File(url.getPath());
                }
                collectDir(file, docs);
            } else if ("jar".equals(url.getProtocol())) {
                String jarUrlString;
                try {
                    jarUrlString = url.toURI().getSchemeSpecificPart();
                } catch (URISyntaxException e) {
                    jarUrlString = url.getPath();
                }

                String[] pathParts = jarUrlString.split("!");
                if (pathParts.length >= 2) {
                    try {
                        File file;
                        try {
                            file = new File(new URI(pathParts[0]));
                        } catch (URISyntaxException e) {
                            file = new File(pathParts[0]);
                        }
                        InputStream inputStream = new FileInputStream(file);
                        String jarPathUrlString = jarUrlString.substring(0, jarUrlString.lastIndexOf('!'));
                        collectJar(inputStream, pathParts, jarPathUrlString, docs);
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }
        }
        return docs;
    }

    private static void collectDir(File dir, Map<URL, Object> docs) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectDir(file, docs);
                } else {
                    String extension = getExtension(file.getName());
                    if ("wsdl".equals(extension) || "xsd".equals(extension)) {
                        try {
                            URL url = file.toURI().toURL();
                            docs.put(url, SDDocumentSource.create(url));
                        } catch (MalformedURLException e) {
                            // do nothing
                        }
                    }
                }
            }
        }
    }

    private static void collectJar(
            InputStream inputStream,
            String[] pathParts,
            String jarPathUrlString,
            Map<URL, Object> docs) {
        String nextPathPart = stripLeadingSlash(pathParts[1]);

        try {
            JarInputStream jarInputStream = new JarInputStream(inputStream);
            JarEntry entry;
            if (pathParts.length == 2) {
                while ((entry = jarInputStream.getNextJarEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String name = entry.getName();
                        if (name.startsWith(nextPathPart)) {
                            String extension = getExtension(name);
                            if ("wsdl".equals(extension) || "xsd".equals(extension)) {
                                String urlString = jarPathUrlString + "!/" + name;
                                try {
                                    URL url = new URI("jar", urlString, null).toURL();
                                    docs.put(url, SDDocumentSource.create(url));
                                } catch (URISyntaxException | MalformedURLException e) {
                                    // do nothing
                                }
                            }
                        }
                    }
                }
            } else {
                while ((entry = jarInputStream.getNextJarEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String name = entry.getName();
                        if (name.equals(nextPathPart)) {
                            String[] subPathParts = Arrays.copyOfRange(pathParts, 1, pathParts.length);
                            collectJar(jarInputStream, subPathParts, jarPathUrlString, docs);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            // do nothing
        }
    }

    private static String getExtension(String name) {
        int index = name.lastIndexOf(".");
        if (index > 0) {
            return name.substring(index + 1);
        }
        return "";
    }

    private static String stripLeadingSlash(String input) {
        if (input.startsWith("/")) {
            return input.substring(1);
        } else {
            return input;
        }
    }
}
