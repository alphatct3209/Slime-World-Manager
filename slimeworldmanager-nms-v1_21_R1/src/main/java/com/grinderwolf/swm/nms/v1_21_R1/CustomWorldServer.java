package com.grinderwolf.swm.nms.v1_21_R1;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.LongArrayTag;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.World;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class CustomWorldServer extends ServerLevel {

    private static final Logger LOGGER = LogManager.getLogger("SWM World");
    private static final ExecutorService WORLD_SAVER_SERVICE = Executors.newFixedThreadPool(4, new ThreadFactoryBuilder()
            .setNameFormat("SWM Pool Thread #%1$d").build());

    @Getter
    private final CraftSlimeWorld slimeWorld;
    private final Object saveLock = new Object();
    private final CustomNBTStorage nbtStorage;

    @Getter
    @Setter
    private boolean ready = false;

    CustomWorldServer(CraftSlimeWorld world, CustomNBTStorage nbtStorage,
                      LevelStorageSource.LevelStorageAccess storageAccess,
                      ResourceKey<Level> dimension, LevelStem levelStem,
                      ChunkProgressListener progressListener, World.Environment env) {
        super(
            MinecraftServer.getServer(),
            MinecraftServer.getServer().executor,
            storageAccess,
            nbtStorage.getWorldData(),
            dimension,
            levelStem,
            progressListener,
            false, // isDebug
            BiomeManager.obfuscateSeed(0L), // SWM worlds use a fixed seed for biome generation
            List.of(), // custom spawners
            false, // tickTime
            null, // randomSequences
            env,
            null, // chunk generator
            null  // biome provider
        );

        this.slimeWorld = world;
        this.nbtStorage = nbtStorage;

        SlimePropertyMap propertyMap = world.getPropertyMap();

        this.serverLevelData.setDifficulty(Difficulty.valueOf(propertyMap.getString(SlimeProperties.DIFFICULTY).toUpperCase()));
        this.serverLevelData.setSpawn(
            new BlockPos(propertyMap.getInt(SlimeProperties.SPAWN_X),
                         propertyMap.getInt(SlimeProperties.SPAWN_Y),
                         propertyMap.getInt(SlimeProperties.SPAWN_Z)),
            0.0f
        );
        super.setSpawnSettings(propertyMap.getBoolean(SlimeProperties.ALLOW_MONSTERS), propertyMap.getBoolean(SlimeProperties.ALLOW_ANIMALS));

        this.pvpMode = propertyMap.getBoolean(SlimeProperties.PVP);
    }

    @Override
    public void save(net.minecraft.util.ProgressListener progressListener, boolean flush, boolean skipSave) {
        if (!slimeWorld.isReadOnly()) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(getWorld()));
            this.getChunkSource().save(flush);

            nbtStorage.saveWorldData(nbtStorage.getWorldData());

            if (MinecraftServer.getServer().isStopped()) {
                save();

                try {
                    slimeWorld.getLoader().unlockWorld(slimeWorld.getName());
                } catch (IOException ex) {
                    LOGGER.error("Failed to unlock the world " + slimeWorld.getName() + ". Please unlock it manually by using the command /swm manualunlock. Stack trace:");
                    ex.printStackTrace();
                } catch (UnknownWorldException ignored) {
                }
            } else {
                WORLD_SAVER_SERVICE.execute(this::save);
            }
        }
    }

    private void save() {
        synchronized (saveLock) {
            try {
                LOGGER.info("Saving world " + slimeWorld.getName() + "...");
                long start = System.currentTimeMillis();
                byte[] serializedWorld = slimeWorld.serialize();
                slimeWorld.getLoader().saveWorld(slimeWorld.getName(), serializedWorld, false);
                LOGGER.info("World " + slimeWorld.getName() + " saved in " + (System.currentTimeMillis() - start) + "ms.");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    LevelChunk getChunk(int x, int z) {
        SlimeChunk slimeChunk = slimeWorld.getChunk(x, z);
        LevelChunk chunk;

        if (slimeChunk == null) {
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(x, z);
            chunk = new LevelChunk(this, pos, net.minecraft.world.level.chunk.UpgradeData.EMPTY);
            Heightmap.primeHeightmaps(chunk, chunk.getStatus().heightmapsAfter());
        } else if (slimeChunk instanceof NMSSlimeChunk) {
            chunk = ((NMSSlimeChunk) slimeChunk).getChunk();
        } else {
            chunk = createChunk(slimeChunk);
            slimeWorld.updateChunk(new NMSSlimeChunk(chunk));
        }

        return chunk;
    }

    private LevelChunk createChunk(SlimeChunk chunk) {
        int x = chunk.getX();
        int z = chunk.getZ();

        LOGGER.debug("Loading chunk (" + x + ", " + z + ") on world " + slimeWorld.getName());

        net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(x, z);

        // Chunk sections
        LOGGER.debug("Loading chunk sections for chunk (" + pos.x + ", " + pos.z + ") on world " + slimeWorld.getName());
        int sectionsCount = this.getSectionsCount();
        LevelChunkSection[] sections = new LevelChunkSection[sectionsCount];

        for (int sectionId = 0; sectionId < chunk.getSections().length && sectionId < sectionsCount; sectionId++) {
            SlimeChunkSection slimeSection = chunk.getSections()[sectionId];

            if (slimeSection != null) {
                LevelChunkSection section = new LevelChunkSection(
                    this.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                );

                LOGGER.debug("ChunkSection #" + sectionId + " - Chunk (" + pos.x + ", " + pos.z + "):");

                net.minecraft.nbt.CompoundTag blocksCompound = new net.minecraft.nbt.CompoundTag();
                blocksCompound.put("Palette", (net.minecraft.nbt.Tag) Converter.convertTag(slimeSection.getPalette()));
                blocksCompound.putLongArray("BlockStates", slimeSection.getBlockStates());

                section.getStates().read(
                    blocksCompound.getList("Palette", 10),
                    blocksCompound.getLongArray("BlockStates")
                );

                section.recalcBlockCounts();
                sections[sectionId] = section;
            }
        }

        Consumer<LevelChunk> loadEntities = (nmsChunk) -> {
            // Load block entities (tile entities)
            LOGGER.debug("Loading tile entities for chunk (" + pos.x + ", " + pos.z + ") on world " + slimeWorld.getName());
            List<com.flowpowered.nbt.CompoundTag> tileEntities = chunk.getTileEntities();
            int loadedEntities = 0;

            if (tileEntities != null) {
                for (com.flowpowered.nbt.CompoundTag tag : tileEntities) {
                    Optional<String> type = tag.getStringValue("id");

                    if (type.isPresent()) {
                        BlockPos entityPos = new BlockPos(
                            tag.getIntValue("x").orElse(0),
                            tag.getIntValue("y").orElse(0),
                            tag.getIntValue("z").orElse(0)
                        );
                        net.minecraft.world.level.block.entity.BlockEntity entity =
                            net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                                entityPos,
                                nmsChunk.getBlockState(entityPos),
                                (net.minecraft.nbt.CompoundTag) Converter.convertTag(tag),
                                this.registryAccess()
                            );

                        if (entity != null) {
                            nmsChunk.setBlockEntity(entity);
                            loadedEntities++;
                        }
                    }
                }
            }

            LOGGER.debug("Loaded " + loadedEntities + " tile entities for chunk (" + pos.x + ", " + pos.z + ") on world " + slimeWorld.getName());
        };

        net.minecraft.world.level.chunk.UpgradeData upgradeData;
        com.flowpowered.nbt.CompoundTag upgradeDataTag = ((CraftSlimeChunk) chunk).getUpgradeData();
        if (upgradeDataTag == null) {
            upgradeData = net.minecraft.world.level.chunk.UpgradeData.EMPTY;
        } else {
            upgradeData = new net.minecraft.world.level.chunk.UpgradeData(
                (net.minecraft.nbt.CompoundTag) Converter.convertTag(upgradeDataTag), this
            );
        }

        LevelChunk nmsChunk = new LevelChunk(
            this, pos, upgradeData, net.minecraft.world.ticks.LevelChunkTicks::new,
            net.minecraft.world.ticks.LevelChunkTicks::new, 0L, sections, loadEntities, null
        );

        // Height Maps
        EnumSet<Heightmap.Types> heightMapTypes = nmsChunk.getStatus().heightmapsAfter();
        CompoundMap heightMaps = chunk.getHeightMaps().getValue();
        EnumSet<Heightmap.Types> unsetHeightMaps = EnumSet.noneOf(Heightmap.Types.class);

        for (Heightmap.Types type : heightMapTypes) {
            String name = type.getSerializationKey();

            if (heightMaps.containsKey(name)) {
                LongArrayTag heightMap = (LongArrayTag) heightMaps.get(name);
                nmsChunk.setHeightmap(type, heightMap.getValue());
            } else {
                unsetHeightMaps.add(type);
            }
        }

        Heightmap.primeHeightmaps(nmsChunk, unsetHeightMaps);
        LOGGER.debug("Loaded chunk (" + pos.x + ", " + pos.z + ") on world " + slimeWorld.getName());

        return nmsChunk;
    }

    void saveChunk(LevelChunk chunk) {
        SlimeChunk slimeChunk = slimeWorld.getChunk(chunk.getPos().x, chunk.getPos().z);

        if (slimeChunk instanceof NMSSlimeChunk) {
            ((NMSSlimeChunk) slimeChunk).setChunk(chunk);
        } else {
            slimeWorld.updateChunk(new NMSSlimeChunk(chunk));
        }
    }
}
