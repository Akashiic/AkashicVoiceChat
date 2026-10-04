package cpw.mods.fml.relauncher;

import java.io.File;

/** Test-only package fixture for Forge Configuration's required game-directory input. */
public final class QualityTestEnvironment implements AutoCloseable {
    private final File previous;
    public QualityTestEnvironment(File isolatedDirectory) {
        if (isolatedDirectory == null || !isolatedDirectory.isDirectory()) {
            throw new IllegalArgumentException("Existing isolated test directory required");
        }
        previous = FMLInjectionData.minecraftHome;
        FMLInjectionData.minecraftHome = isolatedDirectory;
    }
    @Override
    public void close() { FMLInjectionData.minecraftHome = previous; }
}
