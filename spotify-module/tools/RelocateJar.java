import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Isolate bundled AndroidX from Spotify's independently versioned/obfuscated classes. */
public final class RelocateJar {
    public static void main(String[] args) throws Exception {
        Remapper remapper = new Remapper() {
            @Override public String map(String name) {
                if (name.startsWith("androidx/webkit/") || name.startsWith("androidx/core/"))
                    return "kr/ivlis/ivlyricsandroid/privateapi/" + name;
                // Boundary interface FQCNs are a public protocol: WebView loads
                // matching methods by declaring-class name in its own loader.
                // Only helper implementation classes may be privately relocated.
                if (name.startsWith("org/chromium/support_lib_boundary/util/"))
                    return "kr/ivlis/ivlyricsandroid/privateapi/" + name;
                return name;
            }
        };
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream output = new ZipOutputStream(new FileOutputStream(args[1]))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                ClassReader reader = new ClassReader(input.readAllBytes());
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassRemapper(writer, remapper), 0);
                output.putNextEntry(new ZipEntry(remapper.map(reader.getClassName()) + ".class"));
                output.write(writer.toByteArray());
                output.closeEntry();
            }
        }
    }
}
