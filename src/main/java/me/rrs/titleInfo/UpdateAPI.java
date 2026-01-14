package me.rrs.titleInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;

public class UpdateAPI {
    public String getSpigotVersion(String resourceId, TitleInfo plugin) {
        String newVersion = plugin.getDescription().getVersion();

        try {
            InputStream inputStream = (new URI("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId)).toURL().openStream();

            try {
                Scanner scanner = new Scanner(inputStream);

                try {
                    if (scanner.hasNext()) {
                        newVersion = String.valueOf(scanner.next());
                    }
                } catch (Throwable var10) {
                    try {
                        scanner.close();
                    } catch (Throwable var9) {
                        var10.addSuppressed(var9);
                    }

                    throw var10;
                }

                scanner.close();
            } catch (Throwable var11) {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable var8) {
                        var11.addSuppressed(var8);
                    }
                }

                throw var11;
            }

            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return newVersion;
    }
}
