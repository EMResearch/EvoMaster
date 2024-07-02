package org.evomaster.client.java.instrumentation.coverage.methodreplacement;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

public class JsonUtils {

    public static String readStream(InputStream src){
        String content = new BufferedReader(
                new InputStreamReader(src, Charset.defaultCharset()))
                .lines()
                .collect(Collectors.joining(System.lineSeparator()));

        return content;
    }

    public static InputStream toStream(String content){
        return new ByteArrayInputStream(content.getBytes(Charset.defaultCharset()));
    }
}
