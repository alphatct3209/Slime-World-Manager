package com.grinderwolf.swm.nms.v1_21_R1;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper class that provides a fake LevelStorageSource.LevelStorageAccess
 * for SWM worlds that don't actually need disk-based storage.
 */
public class CustomNBTStorage {

    private final SlimeWorld world;
    private final Path tempDir;
    private LevelStorageSource.LevelStorageAccess storageAccess;

    public CustomNBTStorage(SlimeWorld world) {
        this.world = world;
        Path tempPath;
        try {
            tempPath = Files.createTempDirectory("swm_" + world.getName() + "_");
        } catch (IOException e) {
            tempPath = Path.of("swm_temp_" + world.getName());
        }
        this.tempDir = tempPath;
    }

    public LevelStorageSource.LevelStorageAccess createStorageAccess() throws IOException {
        if (storageAccess != null) {
            return storageAccess;
        }
        LevelStorageSource storageSource = LevelStorageSource.createDefault(tempDir.getParent());
        storageAccess = storageSource.createAccess(tempDir.getFileName().toString());
        return storageAccess;
    }

    public CustomWorldData getWorldData() {
        return new CustomWorldData((CraftSlimeWorld) world);
    }

    public void saveWorldData(CustomWorldData worldData) {
        net.minecraft.nbt.CompoundTag gameRulesNbt = worldData.getGameRules().createTag(null);
        com.flowpowered.nbt.CompoundTag gameRules = (com.flowpowered.nbt.CompoundTag)
            Converter.convertTag("gamerules", gameRulesNbt);
        com.flowpowered.nbt.CompoundTag extraData = ((CraftSlimeWorld) world).getExtraData();

        extraData.getValue().remove("gamerules");

        if (!gameRules.getValue().isEmpty()) {
            extraData.getValue().put("gamerules", gameRules);
        }
    }
}
