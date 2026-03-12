package com.grinderwolf.swm.nms.v1_21_R1;

import com.flowpowered.nbt.*;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.*;

@Data
@AllArgsConstructor
public class NMSSlimeChunk implements SlimeChunk {

    private LevelChunk chunk;

    @Override
    public String getWorldName() {
        return chunk.getLevel().getLevelData().getLevelName();
    }

    @Override
    public int getX() {
        return chunk.getPos().x;
    }

    @Override
    public int getZ() {
        return chunk.getPos().z;
    }

    @Override
    public SlimeChunkSection[] getSections() {
        LevelChunkSection[] nmsSections = chunk.getSections();
        int sectionCount = nmsSections.length;
        SlimeChunkSection[] sections = new SlimeChunkSection[sectionCount];

        for (int sectionId = 0; sectionId < sectionCount; sectionId++) {
            LevelChunkSection section = nmsSections[sectionId];

            if (section != null && !section.hasOnlyAir()) {
                section.recalcBlockCounts();

                // Block light
                NibbleArray blockLightArray = null;
                NibbleArray skyLightArray = null;

                // Block data - serialize palette and block states
                net.minecraft.nbt.CompoundTag blocksCompound = new net.minecraft.nbt.CompoundTag();
                section.getStates().write(blocksCompound, "Palette", "BlockStates");
                net.minecraft.nbt.ListTag paletteNbt = blocksCompound.getList("Palette", 10);
                ListTag<CompoundTag> palette = (ListTag<CompoundTag>) Converter.convertTag("", paletteNbt);
                long[] blockStates = blocksCompound.getLongArray("BlockStates");

                sections[sectionId] = new CraftSlimeChunkSection(null, null, palette, blockStates, blockLightArray, skyLightArray);
            }
        }

        return sections;
    }

    @Override
    public CompoundTag getHeightMaps() {
        CompoundMap heightMaps = new CompoundMap();

        for (Map.Entry<net.minecraft.world.level.levelgen.Heightmap.Types, net.minecraft.world.level.levelgen.Heightmap> entry : chunk.heightmaps.entrySet()) {
            net.minecraft.world.level.levelgen.Heightmap.Types type = entry.getKey();
            net.minecraft.world.level.levelgen.Heightmap map = entry.getValue();
            heightMaps.put(type.getSerializationKey(), new LongArrayTag(type.getSerializationKey(), map.getRawData()));
        }

        return new CompoundTag("", heightMaps);
    }

    @Override
    public int[] getBiomes() {
        // In 1.21, biomes are stored per-section; return an empty array as biomes are now stored differently
        return new int[0];
    }

    @Override
    public List<CompoundTag> getTileEntities() {
        List<CompoundTag> tileEntities = new ArrayList<>();

        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            net.minecraft.nbt.CompoundTag entityNbt = entity.saveWithFullMetadata(chunk.getLevel().registryAccess());
            tileEntities.add((CompoundTag) Converter.convertTag("", entityNbt));
        }

        return tileEntities;
    }

    @Override
    public List<CompoundTag> getEntities() {
        // In 1.21, entities are stored separately in entity chunks
        // Return empty list as entities are managed by the server's entity storage
        return new ArrayList<>();
    }
}
