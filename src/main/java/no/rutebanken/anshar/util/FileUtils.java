package no.rutebanken.anshar.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils {

    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    public static List<String> listDirectories(String directoryPath) {

        File organisationDirectory = new File(directoryPath);


        if (organisationDirectory.isDirectory()) {

            File[] directories = organisationDirectory.listFiles(File::isDirectory);
            if (directories == null) {
                return new ArrayList<>();
            }

            return Arrays.stream(directories)
                    .map(File::getName)
                    .collect(Collectors.toList());

        }
        return new ArrayList<>();
    }

    public static Set<String> listCSVFiles(String directoryPath) {
        Set<String> results = new HashSet<>();

        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".csv"))
                    .map(path -> path.getFileName().toString())
                    .forEach(results::add);
        } catch (IOException e) {
            log.error("Failed to list files in directory {}", directoryPath, e);
        }
        return results;
    }
}
