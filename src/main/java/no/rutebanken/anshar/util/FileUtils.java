package no.rutebanken.anshar.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FileUtils {

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
}
